package com.ziprun.reassignment.service;

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
import org.springframework.stereotype.Service;

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

  public ReassignmentService(
      AgentRepository agentRepository,
      OrderRepository orderRepository,
      ReassignmentSuggestionRepository suggestionRepository,
      RoutingStrategyResolver strategyResolver,
      AgentEligibilityFilter eligibilityFilter) {
    this.agentRepository = agentRepository;
    this.orderRepository = orderRepository;
    this.suggestionRepository = suggestionRepository;
    this.strategyResolver = strategyResolver;
    this.eligibilityFilter = eligibilityFilter;
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

    List<Agent> availableAgents = agentRepository.findByStatus(AgentStatus.AVAILABLE);
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

    AgentRecommendation top = recommendations.get(0);

    ReassignmentSuggestion suggestion = new ReassignmentSuggestion();
    suggestion.setOrderId(order.getId());
    suggestion.setRecommendedAgentId(top.agentId());
    suggestion.setConfidence(top.confidence());
    suggestion.setReasoning(top.reasoning());
    suggestion.setStatus(SuggestionStatus.PENDING);
    suggestion.setTriggerReason(context.triggerReason());

    order.setStatus(OrderStatus.REASSIGNMENT_PENDING);
    orderRepository.save(order);

    return suggestionRepository.save(suggestion);
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
            order.setAssignedAgentId(suggestion.getRecommendedAgentId());
            order.setStatus(OrderStatus.REASSIGNED);
          } else if (newStatus == SuggestionStatus.REJECTED) {
            order.setStatus(OrderStatus.ASSIGNED);
          }
          orderRepository.save(order);
        });

    return suggestion;
  }
}
