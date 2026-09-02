package com.ziprun.reassignment.routing;

/** A single ranked recommendation returned by a {@link RoutingStrategy}. */
public record AgentRecommendation(String agentId, double confidence, String reasoning) {}
