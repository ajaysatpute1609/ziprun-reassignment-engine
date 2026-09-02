package com.ziprun.reassignment.ai;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.domain.TriggerReason;
import com.ziprun.reassignment.routing.RoutingContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds a genuinely different prompt per trigger reason (AGT-3) rather than
 * one document with a field appended. A first assignment, a recovery from
 * an agent going offline, and a proactive SLA-risk warning are three
 * different situations and should be reasoned about differently — the
 * re-plan and SLA-risk prompts are framed as situation reports (what's
 * happening, why it's urgent), not an updated order form.
 */
@Component
public class PromptBuilder {

  public String build(Order order, List<Agent> availableAgents, RoutingContext context) {
    String roster = formatRoster(availableAgents);
    String orderMeta = formatOrderMeta(order);
    String schema =
        "Respond with ONLY a JSON object, no markdown, in exactly this shape: "
            + "{\"agentId\": \"<one of the ids above>\", \"confidence\": <0.0-1.0>, "
            + "\"reasoning\": \"<one or two plain-English sentences an ops person can act on>\"}";

    if (context.triggerReason() == TriggerReason.SLA_AT_RISK) {
      return buildSlaAtRiskPrompt(order, orderMeta, roster, schema, context);
    }

    if (context.triggerReason() == TriggerReason.AGENT_OFFLINE) {
      return """
          SITUATION REPORT — AGENT OFFLINE RECOVERY

          Agent %s has gone OFFLINE mid-shift. Order %s ("%s")%s was assigned to
          them and is now stranded with no active carrier. This is a recovery
          action, not a routine assignment — the previous assignment to %s is
          void and must not be recommended again.

          Available agents right now (id, current active order count, zone,
          capacity, heavy-order capability):
          %s

          Recommend the single best available agent to take over this order.
          Prioritize agents already in or near the pickup zone and with lower
          current load, since they have more capacity to absorb an unplanned
          order on short notice. Do not recommend an agent who cannot carry a
          HEAVY order if this order is HEAVY. Explain your reasoning in terms
          an operations manager can act on immediately.

          %s
          """
          .formatted(
              context.offlineAgentId(),
              order.getId(),
              order.getDescription(),
              orderMeta,
              context.offlineAgentId(),
              roster,
              schema);
    }

    return """
        NEW ORDER ASSIGNMENT

        A new order needs an agent assigned. This is a first assignment, not
        a recovery — there is no prior failure to account for.

        Order: %s ("%s")%s

        Available agents right now (id, current active order count, zone,
        capacity, heavy-order capability):
        %s

        Recommend the single best available agent for this order, balancing
        current load and zone proximity across the roster. Do not recommend
        an agent who cannot carry a HEAVY order if this order is HEAVY.
        Explain your reasoning in terms an operations manager can act on
        immediately.

        %s
        """
        .formatted(order.getId(), order.getDescription(), orderMeta, roster, schema);
  }

  private String buildSlaAtRiskPrompt(
      Order order, String orderMeta, String roster, String schema, RoutingContext context) {
    Integer mins = context.slaMinutesRemaining();
    String urgency =
        (mins != null && mins < 0)
            ? "This order has ALREADY BREACHED its SLA deadline by " + Math.abs(mins) + " minutes."
            : "This order has only " + mins + " minutes left before its SLA deadline breaches.";

    return """
        SITUATION REPORT — SLA BREACH RISK (PROACTIVE)

        No agent has failed or gone offline. This is a proactive warning:
        Order %s ("%s")%s is at risk of missing its delivery SLA. %s The
        currently assigned agent has not been marked unavailable — they may
        simply be overloaded, delayed, or slower than the delivery window
        allows. This is not a recovery from a failure; it is an early
        intervention to prevent one.

        Available agents right now (id, current active order count, zone,
        capacity, heavy-order capability):
        %s

        Recommend the single best available agent to take over this order
        immediately, prioritizing agents who can realistically beat the
        remaining time — lower current load and zone proximity matter more
        here than usual, since time is the binding constraint. Do not
        recommend an agent who cannot carry a HEAVY order if this order is
        HEAVY. Explain your reasoning in terms an operations manager can act
        on immediately, and mention the time pressure explicitly.

        %s
        """
        .formatted(order.getId(), order.getDescription(), orderMeta, urgency, roster, schema);
  }

  private String formatOrderMeta(Order order) {
    StringBuilder sb = new StringBuilder();
    if (order.getPickupZone() != null) {
      sb.append(", pickup zone=").append(order.getPickupZone());
    }
    if (order.getDropoffZone() != null) {
      sb.append(", dropoff zone=").append(order.getDropoffZone());
    }
    if (order.getWeightClass() != null) {
      sb.append(", weight class=").append(order.getWeightClass());
    }
    return sb.toString();
  }

  private String formatRoster(List<Agent> agents) {
    if (agents.isEmpty()) {
      return "(none available)";
    }
    StringBuilder sb = new StringBuilder();
    for (Agent agent : agents) {
      sb.append("- ")
          .append(agent.getId())
          .append(" (")
          .append(agent.getName())
          .append("): ")
          .append(agent.getActiveOrderCount())
          .append(" active orders")
          .append(agent.getCurrentZone() != null ? ", zone=" + agent.getCurrentZone() : "")
          .append(agent.getMaxCapacity() != null ? ", capacity=" + agent.getMaxCapacity() : "")
          .append(!agent.isCanHandleHeavy() ? ", cannot carry HEAVY orders" : "")
          .append("\n");
    }
    return sb.toString();
  }
}
