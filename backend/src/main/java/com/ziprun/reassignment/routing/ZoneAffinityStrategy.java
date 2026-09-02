package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sprint 2 strategy: prefers agents already in (or near) the order's pickup
 * zone, breaking ties by current load. This is the exact extension point
 * described in ADR-5 on {@code main} — implementing {@link RoutingStrategy}
 * and registering the bean under a new name is the entire integration; no
 * other class changed to add this.
 *
 * Agents/orders without zone data (nullable fields) simply fall back to
 * pure load-based ordering, so this strategy degrades gracefully rather
 * than excluding un-zoned agents outright.
 */
@Component("zone")
public class ZoneAffinityStrategy implements RoutingStrategy {

  @Override
  public List<AgentRecommendation> recommend(
      Order order, List<Agent> availableAgents, RoutingContext context) {
    String pickupZone = order.getPickupZone();

    return availableAgents.stream()
        .sorted(
            Comparator.comparing((Agent a) -> !sameZone(a, pickupZone))
                .thenComparingInt(Agent::getActiveOrderCount))
        .map(agent -> new AgentRecommendation(agent.getId(), confidenceFor(agent, pickupZone), reasonFor(agent, pickupZone)))
        .toList();
  }

  private boolean sameZone(Agent agent, String pickupZone) {
    return pickupZone != null && pickupZone.equalsIgnoreCase(agent.getCurrentZone());
  }

  private double confidenceFor(Agent agent, String pickupZone) {
    return sameZone(agent, pickupZone) ? 0.8 : 0.55;
  }

  private String reasonFor(Agent agent, String pickupZone) {
    if (sameZone(agent, pickupZone)) {
      return "Zone-affinity: already in "
          + agent.getCurrentZone()
          + " (matches pickup zone), "
          + agent.getActiveOrderCount()
          + " active orders.";
    }
    return "Zone-affinity: no agent currently in pickup zone '"
        + pickupZone
        + "'; falling back to lowest load ("
        + agent.getCurrentZone()
        + ", "
        + agent.getActiveOrderCount()
        + " active orders).";
  }
}
