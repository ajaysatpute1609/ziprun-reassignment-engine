package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Deterministic fallback strategy — no external dependencies. Recommends the
 * available agent carrying the fewest active orders. Also used as the
 * fallback target when the AI strategy fails for any reason.
 */
@Component("rule")
public class RuleBasedRoutingStrategy implements RoutingStrategy {

  @Override
  public List<AgentRecommendation> recommend(
      Order order, List<Agent> availableAgents, RoutingContext context) {
    return availableAgents.stream()
        .sorted(Comparator.comparingInt(Agent::getActiveOrderCount))
        .map(
            agent ->
                new AgentRecommendation(
                    agent.getId(),
                    0.6,
                    "Rule-based: lowest current load ("
                        + agent.getActiveOrderCount()
                        + " active orders)."))
        .toList();
  }
}
