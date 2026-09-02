package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.TriggerReason;

/**
 * Carries situational context alongside the order/agent data so a strategy
 * (especially the AI one) knows *why* it's being asked to route — a first
 * assignment and a recovery from an agent going offline are different
 * situations and should be reasoned about differently.
 */
public record RoutingContext(TriggerReason triggerReason, String offlineAgentId) {

  public static RoutingContext initial() {
    return new RoutingContext(TriggerReason.INITIAL, null);
  }

  public static RoutingContext agentOffline(String offlineAgentId) {
    return new RoutingContext(TriggerReason.AGENT_OFFLINE, offlineAgentId);
  }
}
