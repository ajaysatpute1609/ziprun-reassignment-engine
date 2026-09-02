package com.ziprun.reassignment.event;

import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.domain.OrderStatus;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.routing.RoutingContext;
import com.ziprun.reassignment.service.ReassignmentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * The agentic loop (T-4): observe (agent goes OFFLINE) -> reason (which
 * orders are stranded?) -> act (queue a suggestion per order) -> checkpoint
 * (ops approves via PATCH /suggestions/{id}). The system never auto-assigns
 * here — see ADR-4 and AGT-1/AGT-2.
 */
@Component
public class AgentOfflineListener {

  private static final Logger log = LoggerFactory.getLogger(AgentOfflineListener.class);

  private final OrderRepository orderRepository;
  private final ReassignmentService reassignmentService;

  public AgentOfflineListener(
      OrderRepository orderRepository, ReassignmentService reassignmentService) {
    this.orderRepository = orderRepository;
    this.reassignmentService = reassignmentService;
  }

  @Async("reassignmentExecutor")
  @EventListener
  public void onAgentOffline(AgentOfflineEvent event) {
    String offlineAgentId = event.agentId();

    List<Order> strandedOrders =
        orderRepository.findByAssignedAgentIdAndStatusIn(
            offlineAgentId, List.of(OrderStatus.ASSIGNED, OrderStatus.REASSIGNMENT_PENDING));

    log.info(
        "Agent {} went OFFLINE — {} order(s) stranded, re-planning...",
        offlineAgentId,
        strandedOrders.size());

    RoutingContext context = RoutingContext.agentOffline(offlineAgentId);

    for (Order order : strandedOrders) {
      // Idempotency (AGT-4): don't duplicate a pending re-plan suggestion.
      if (reassignmentService.hasPendingOfflineSuggestion(order.getId())) {
        log.info(
            "Skipping order {} — a PENDING AGENT_OFFLINE suggestion already exists", order.getId());
        continue;
      }
      try {
        reassignmentService.suggestForOrder(order.getId(), context);
      } catch (Exception e) {
        // Even if this single order's re-plan fails outright, keep going for
        // the rest — one bad order shouldn't stop the whole re-plan.
        log.error("Re-plan failed for order {}: {}", order.getId(), e.getMessage(), e);
      }
    }
  }
}
