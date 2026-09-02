package com.ziprun.reassignment.event;

/**
 * Published when an agent transitions to OFFLINE. Consumed asynchronously by
 * {@code AgentOfflineListener} — the request thread that publishes this
 * never waits for the listener to finish (see ADR-4).
 */
public record AgentOfflineEvent(String agentId) {}
