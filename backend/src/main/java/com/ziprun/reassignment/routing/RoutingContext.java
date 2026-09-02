package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.TriggerReason;

/**
 * Carries situational context alongside the order/agent data so a strategy
 * (especially the AI one) knows *why* it's being asked to route — a first
 * assignment, a recovery from an agent going offline, and a proactive
 * SLA-risk warning are three different situations and should be reasoned
 * about differently. Modeling "why is this suggestion happening" as data
 * here (rather than a hardcoded branch per scenario elsewhere) is what let
 * sprint 3's proactive trigger reuse the entire routing/suggestion pipeline
 * unchanged — see SPRINT2-ADR-4.
 */
public record RoutingContext(
    TriggerReason triggerReason, String offlineAgentId, Integer slaMinutesRemaining) {

  public static RoutingContext initial() {
    return new RoutingContext(TriggerReason.INITIAL, null, null);
  }

  public static RoutingContext agentOffline(String offlineAgentId) {
    return new RoutingContext(TriggerReason.AGENT_OFFLINE, offlineAgentId, null);
  }

  public static RoutingContext slaAtRisk(int minutesRemaining) {
    return new RoutingContext(TriggerReason.SLA_AT_RISK, null, minutesRemaining);
  }
}
