package com.ziprun.reassignment.service;

import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.LLMResponseParser;
import com.ziprun.reassignment.ai.LLMSuggestion;
import com.ziprun.reassignment.ai.PromptBuilder;
import com.ziprun.reassignment.domain.*;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.ReassignmentSuggestionRepository;
import com.ziprun.reassignment.routing.AgentEligibilityFilter;
import com.ziprun.reassignment.routing.AgentRecommendation;
import com.ziprun.reassignment.routing.RoutingContext;
import com.ziprun.reassignment.routing.RoutingStrategy;
import com.ziprun.reassignment.routing.RoutingStrategyResolver;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Single entry point for turning "an order needs a routing decision" into a
 * persisted {@link ReassignmentSuggestion}. Used by both the on-demand HTTP
 * endpoint and the async agentic re-planning loop (T-4) — neither caller
 * talks to a {@link RoutingStrategy} directly (see ADR-1).
 */
@Service
public class ReassignmentService {

  private static final Logger log = LoggerFactory.getLogger(ReassignmentService.class);

  private final AgentRepository agentRepository;
  private final OrderRepository orderRepository;
  private final ReassignmentSuggestionRepository suggestionRepository;
  private final RoutingStrategyResolver strategyResolver;
  private final AgentEligibilityFilter eligibilityFilter;

  // Used only by the SSE streaming bonus endpoint (streamSuggestion below).
  // Deliberately AI-specific, unlike the rest of this class, because
  // "stream tokens as they arrive" isn't expressible through the
  // synchronous RoutingStrategy contract — see SPRINT2-ADR-5.
  private final PromptBuilder promptBuilder;
  private final LLMGateway llmGateway;
  private final LLMResponseParser responseParser;

  public ReassignmentService(
      AgentRepository agentRepository,
      OrderRepository orderRepository,
      ReassignmentSuggestionRepository suggestionRepository,
      RoutingStrategyResolver strategyResolver,
      AgentEligibilityFilter eligibilityFilter,
      PromptBuilder promptBuilder,
      LLMGateway llmGateway,
      LLMResponseParser responseParser) {
    this.agentRepository = agentRepository;
    this.orderRepository = orderRepository;
    this.suggestionRepository = suggestionRepository;
    this.strategyResolver = strategyResolver;
    this.eligibilityFilter = eligibilityFilter;
    this.promptBuilder = promptBuilder;
    this.llmGateway = llmGateway;
    this.responseParser = responseParser;
  }

  /**
   * Runs the active strategy for a single order and persists a suggestion.
   * Falls back to the rule-based strategy if the active strategy throws
   * (ADR-3) so a caller always gets a usable suggestion back rather than an
   * exception or a silent drop.
   */
  public ReassignmentSuggestion suggestForOrder(String orderId, RoutingContext context) {
    Order order =
        orderRepository
            .findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown order: " + orderId));

    // Don't allow two PENDING suggestions for the same order: each pending
    // suggestion reserves capacity on a recommended agent, and overlapping
    // reservations would make load-balancing decisions stale.
    if (suggestionRepository.existsByOrderIdAndStatus(order.getId(), SuggestionStatus.PENDING)) {
      throw new IllegalStateException(
          "A pending suggestion already exists for order " + orderId + "; resolve it first");
    }

    List<Agent> availableAgents = agentRepository.findByStatusNot(AgentStatus.OFFLINE);
    List<Agent> eligibleAgents = eligibilityFilter.eligibleFor(order, availableAgents);

    if (eligibleAgents.isEmpty()) {
      throw new IllegalStateException(
          "No eligible agent for order " + orderId + " (capacity/weight-class constraints)");
    }

    RoutingStrategy active = strategyResolver.active();
    List<AgentRecommendation> recommendations;
    try {
      recommendations = active.recommend(order, eligibleAgents, context);
      if (recommendations.isEmpty()) {
        throw new IllegalStateException("Active strategy returned no recommendations");
      }
    } catch (Exception e) {
      log.warn(
          "Active routing strategy failed for order {} ({}); falling back to rule-based: {}",
          orderId,
          context.triggerReason(),
          e.getMessage());
      recommendations = strategyResolver.fallback().recommend(order, eligibleAgents, context);
    }

    return persist(order, recommendations.get(0), context.triggerReason());
  }

  private ReassignmentSuggestion persist(
      Order order, AgentRecommendation top, TriggerReason triggerReason) {
    ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
    suggestion.setOrderId(order.getId());
    suggestion.setRecommendedAgentId(top.agentId());
    suggestion.setConfidence(top.confidence());
    suggestion.setReasoning(top.reasoning());
    suggestion.setStatus(SuggestionStatus.PENDING);
    suggestion.setTriggerReason(triggerReason);

    order.setStatus(OrderStatus.REASSIGNMENT_PENDING);
    orderRepository.save(order);

    ReassignmentSuggestion saved = suggestionRepository.save(suggestion);

    // Reserve capacity on the recommended agent immediately. Without this,
    // a batch of stranded orders (e.g. an agent going offline with 3
    // orders) all get routed to the same lowest-load agent because the
    // previous suggestion's load is not reflected until it is accepted.
    adjustAgentLoad(top.agentId(), +1);

    return saved;
  }

  /**
   * SSE streaming bonus (T-3): streams the AI's reasoning token-by-token to
   * the emitter, then persists the final validated suggestion exactly like
   * {@link #suggestForOrder} does. Falls back to the rule-based strategy
   * (non-streamed) on any failure, consistent with ADR-3 — a streaming
   * failure still produces a usable suggestion, never a silent drop.
   *
   * Runs on the same dedicated executor as the agentic loop so the HTTP
   * thread that opened the SSE connection is freed immediately; the
   * controller returns the {@link SseEmitter} right away and this method
   * pushes events onto it asynchronously.
   */
  @Async("reassignmentExecutor")
  public void streamSuggestion(String orderId, RoutingContext context, SseEmitter emitter) {
    try {
      Order order =
          orderRepository
              .findById(orderId)
              .orElseThrow(() -> new IllegalArgumentException("Unknown order: " + orderId));

      List<Agent> availableAgents = agentRepository.findByStatusNot(AgentStatus.OFFLINE);
      List<Agent> eligibleAgents = eligibilityFilter.eligibleFor(order, availableAgents);

      if (eligibleAgents.isEmpty()) {
        emitter.send(SseEmitter.event().name("error").data("No eligible agent"));
        emitter.complete();
        return;
      }

      String prompt = promptBuilder.build(order, eligibleAgents, context);
      StringBuilder full = new StringBuilder();

      try {
        llmGateway.streamLLM(
            prompt,
            token -> {
              full.append(token);
              try {
                emitter.send(SseEmitter.event().name("token").data(token));
              } catch (Exception e) {
                // Client likely disconnected — nothing more we can do for this token.
                log.debug("Failed to emit token for order {}: {}", orderId, e.getMessage());
              }
            });

        LLMSuggestion validated = responseParser.parseAndValidate(full.toString(), eligibleAgents);
        ReassignmentSuggestion suggestion =
            persist(
                order,
                new AgentRecommendation(
                    validated.agentId(), validated.confidence(), validated.reasoning()),
                context.triggerReason());

        emitter.send(SseEmitter.event().name("suggestion").data(suggestion));
        emitter.complete();
      } catch (Exception e) {
        log.warn(
            "Streaming AI suggestion failed for order {}; falling back to rule-based: {}",
            orderId,
            e.getMessage());
        AgentRecommendation fallback =
            strategyResolver.fallback().recommend(order, eligibleAgents, context).get(0);
        ReassignmentSuggestion suggestion = persist(order, fallback, context.triggerReason());

        emitter.send(SseEmitter.event().name("fallback").data("AI streaming failed; used rule-based fallback"));
        emitter.send(SseEmitter.event().name("suggestion").data(suggestion));
        emitter.complete();
      }
    } catch (Exception e) {
      emitter.completeWithError(e);
    }
  }

  /**
   * Idempotency guard for the agentic loop (AGT-4): skip if a PENDING
   * AGENT_OFFLINE suggestion already exists for this order.
   */
  public boolean hasPendingOfflineSuggestion(String orderId) {
    return suggestionRepository.existsByOrderIdAndTriggerReasonAndStatus(
        orderId, TriggerReason.AGENT_OFFLINE, SuggestionStatus.PENDING);
  }

  public ReassignmentSuggestion resolveSuggestion(Long suggestionId, SuggestionStatus newStatus) {
    ReassignmentSuggestion suggestion =
        suggestionRepository
            .findById(suggestionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown suggestion: " + suggestionId));

    suggestion.setStatus(newStatus);
    suggestionRepository.save(suggestion);

    Optional<Order> orderOpt = orderRepository.findById(suggestion.getOrderId());
    orderOpt.ifPresent(
        order -> {
          if (newStatus == SuggestionStatus.ACCEPTED) {
            String previousAgentId = order.getAssignedAgentId();
            String newAgentId = suggestion.getRecommendedAgentId();

            // Bugfix (SPRINT2-ADR-6): accepting a suggestion moves the
            // order's workload from one agent to another. The new agent's
            // capacity was already reserved when the suggestion was created
            // (see persist()), so we only decrement the previous agent here.
            // Without decrementing the previous agent, the old agent would keep
            // appearing over-loaded forever.
            if (!java.util.Objects.equals(previousAgentId, newAgentId)) {
              adjustAgentLoad(previousAgentId, -1);
            }

            order.setAssignedAgentId(newAgentId);
            order.setStatus(OrderStatus.REASSIGNED);
          } else if (newStatus == SuggestionStatus.REJECTED) {
            // Suggestion was pending, so we had reserved capacity on the
            // recommended agent. Release that reservation and hand the
            // order back to the original agent.
            adjustAgentLoad(suggestion.getRecommendedAgentId(), -1);
            order.setStatus(OrderStatus.ASSIGNED);
          }
          orderRepository.save(order);
        });

    return suggestion;
  }

  private void adjustAgentLoad(String agentId, int delta) {
    if (agentId == null) {
      return;
    }
    agentRepository
        .findById(agentId)
        .ifPresent(
            agent -> {
              agent.setActiveOrderCount(Math.max(0, agent.getActiveOrderCount() + delta));
              agentRepository.save(agent);
            });
  }
}
