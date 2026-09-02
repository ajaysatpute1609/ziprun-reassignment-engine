package com.ziprun.reassignment.ai;

/** Parsed, validated shape of the LLM's JSON response. */
public record LLMSuggestion(String agentId, double confidence, String reasoning) {}
