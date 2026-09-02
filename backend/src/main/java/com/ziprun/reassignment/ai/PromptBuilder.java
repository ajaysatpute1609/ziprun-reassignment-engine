package com.ziprun.reassignment.ai;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.routing.RoutingContext;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds two genuinely different prompts (AGT-3) rather than one document
 * with a field appended. An initial assignment and a recovery from an agent
 * going offline call for different reasoning: the re-plan prompt is framed
 * as a situation report (what failed, what's stranded, why this is urgent),
 * not an updated order form.
 */
@Component
public class PromptBuilder {

  public String build(Order order, List<Agent> availableAgents, RoutingContext context) {
    String roster = formatRoster(availableAgents);
    String schema =
        "Respond with ONLY a JSON object, no markdown, in exactly this shape: "
            + "{\"agentId\": \"<one of the ids above>\", \"confidence\": <0.0-1.0>, "
            + "\"reasoning\": \"<one or two plain-English sentences an ops person can act on>\"}";

    if (context.triggerReason() == com.ziprun.reassignment.domain.TriggerReason.AGENT_OFFLINE) {
      return """
          SITUATION REPORT — AGENT OFFLINE RECOVERY

          Agent %s has gone OFFLINE mid-shift. Order %s ("%s") was assigned to
          them and is now stranded with no active carrier. This is a recovery
          action, not a routine assignment — the previous assignment to %s is
          void and must not be recommended again.

          Available agents right now (id, current active order count):
          %s

          Recommend the single best available agent to take over this order.
          Prioritize agents with lower current load, since they have more
          capacity to absorb an unplanned order on short notice. Explain your
          reasoning in terms an operations manager can act on immediately.

          %s
          """
          .formatted(
              context.offlineAgentId(),
              order.getId(),
              order.getDescription(),
              context.offlineAgentId(),
              roster,
              schema);
    }

    return """
        NEW ORDER ASSIGNMENT

        A new order needs an agent assigned. This is a first assignment, not
        a recovery — there is no prior failure to account for.

        Order: %s ("%s")

        Available agents right now (id, current active order count):
        %s

        Recommend the single best available agent for this order, balancing
        current load across the roster. Explain your reasoning in terms an
        operations manager can act on immediately.

        %s
        """
        .formatted(order.getId(), order.getDescription(), roster, schema);
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
          .append(" active orders\n");
    }
    return sb.toString();
  }
}
