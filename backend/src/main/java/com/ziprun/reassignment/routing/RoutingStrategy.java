package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import java.util.List;

/**
 * Routing contract. Implementations pick the best available agent(s) for a
 * given order. Called from two places — the on-demand HTTP endpoint and the
 * async agentic re-planning loop — so implementations must not assume
 * anything about the calling context beyond what {@link RoutingContext}
 * carries.
 *
 * Sprint 2 adds a ZoneAffinityStrategy: implementing this interface and
 * annotating the implementation {@code @Component("zone")} is the entire
 * integration — no changes to existing strategies, the resolver, or either
 * caller required.
 */
public interface RoutingStrategy {
  List<AgentRecommendation> recommend(
      Order order, List<Agent> availableAgents, RoutingContext context);
}
