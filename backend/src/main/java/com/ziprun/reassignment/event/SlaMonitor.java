package com.ziprun.reassignment.event;

import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.domain.OrderStatus;
import com.ziprun.reassignment.domain.SuggestionStatus;
import com.ziprun.reassignment.domain.TriggerReason;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.ReassignmentSuggestionRepository;
import com.ziprun.reassignment.routing.RoutingContext;
import com.ziprun.reassignment.service.ReassignmentService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sprint 3 (AGT-5 seam): the proactive half of the agentic loop. Unlike
 * {@link AgentOfflineListener}, which reacts to a state-change event, this
 * is a scheduled monitor — deliberately so, because there is no discrete
 * "event" for "time is running out." Observe (order nearing SLA deadline)
 * -> reason (which orders are actually at risk?) -> act (queue a
 * suggestion) -> checkpoint (ops approves) — the same four-part loop as
 * T-4, just triggered by a clock instead of a status change.
 *
 * Reuses {@link ReassignmentService#suggestForOrder} unchanged — this is
 * the extension seam described in ADR-5 on {@code main}: the routing
 * pipeline doesn't care whether the reason it was invoked is an agent going
 * offline or a scheduled deadline check.
 */
@Component
public class SlaMonitor {

  private static final Logger log = LoggerFactory.getLogger(SlaMonitor.class);

  private final OrderRepository orderRepository;
  private final ReassignmentSuggestionRepository suggestionRepository;
  private final ReassignmentService reassignmentService;

  @Value("${sla.risk.threshold-minutes:10}")
  private int riskThresholdMinutes;

  public SlaMonitor(
      OrderRepository orderRepository,
      ReassignmentSuggestionRepository suggestionRepository,
      ReassignmentService reassignmentService) {
    this.orderRepository = orderRepository;
    this.suggestionRepository = suggestionRepository;
    this.reassignmentService = reassignmentService;
  }

  @Scheduled(fixedRateString = "${sla.check.interval-ms:30000}")
  public void checkForOrdersAtRisk() {
    List<Order> assignedOrders = orderRepository.findByStatus(OrderStatus.ASSIGNED);

    for (Order order : assignedOrders) {
      if (order.getSlaDeadline() == null) {
        continue;
      }

      int minutesRemaining = minutesUntil(order.getSlaDeadline());
      if (minutesRemaining > riskThresholdMinutes) {
        continue; // plenty of time — not at risk yet
      }

      // Idempotency, same pattern as AGT-4: don't queue a second SLA-risk
      // suggestion while one is still PENDING for this order.
      boolean alreadyQueued =
          suggestionRepository.existsByOrderIdAndTriggerReasonAndStatus(
              order.getId(), TriggerReason.SLA_AT_RISK, SuggestionStatus.PENDING);
      if (alreadyQueued) {
        continue;
      }

      log.info(
          "Order {} is at SLA risk ({} min remaining, threshold {}) — queuing proactive suggestion",
          order.getId(),
          minutesRemaining,
          riskThresholdMinutes);

      try {
        reassignmentService.suggestForOrder(
            order.getId(), RoutingContext.slaAtRisk(minutesRemaining));
      } catch (Exception e) {
        log.error("Proactive SLA re-plan failed for order {}: {}", order.getId(), e.getMessage(), e);
      }
    }
  }

  private int minutesUntil(Instant deadline) {
    return (int) ChronoUnit.MINUTES.between(Instant.now(), deadline);
  }
}
