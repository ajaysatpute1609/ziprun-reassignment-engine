package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import com.ziprun.reassignment.domain.WeightClass;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Sprint 2: hard eligibility rules that apply regardless of which
 * RoutingStrategy is active — no agent should ever be recommended past
 * their capacity, and a HEAVY order should never land on an agent who
 * can't carry it. Deliberately kept out of individual strategies: it's a
 * business rule about *who is allowed to be considered at all*, not a
 * strategy's opinion about *who is best*. Every RoutingStrategy
 * (rule-based, AI, zone) receives an already-eligible agent list, so
 * "never exceed capacity" can't be accidentally skipped by a future
 * strategy that forgets to check it itself.
 */
@Component
public class AgentEligibilityFilter {

  public List<Agent> eligibleFor(Order order, List<Agent> candidates) {
    return candidates.stream()
        .filter(agent -> hasCapacity(agent))
        .filter(agent -> canCarry(agent, order))
        .toList();
  }

  private boolean hasCapacity(Agent agent) {
    return agent.getMaxCapacity() == null || agent.getActiveOrderCount() < agent.getMaxCapacity();
  }

  private boolean canCarry(Agent agent, Order order) {
    return order.getWeightClass() != WeightClass.HEAVY || agent.isCanHandleHeavy();
  }
}
