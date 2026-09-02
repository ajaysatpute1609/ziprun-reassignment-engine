package com.ziprun.reassignment.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziprun.reassignment.domain.Agent;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Shared JSON-parsing and agent-id validation logic, used by both
 * {@code AiRoutingStrategy} (synchronous) and the SSE streaming endpoint.
 * Extracted so both code paths reject a hallucinated agent id identically
 * — a validation rule that's only checked in one of the two call sites
 * would be a bug waiting to happen.
 */
@Component
public class LLMResponseParser {

  private static final Logger log = LoggerFactory.getLogger(LLMResponseParser.class);

  private final ObjectMapper objectMapper = new ObjectMapper();

  public LLMSuggestion parse(String raw) {
    try {
      String cleaned = raw.trim().replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
      JsonNode node = objectMapper.readTree(cleaned);
      String agentId = node.get("agentId").asText();
      double confidence = node.get("confidence").asDouble();
      String reasoning = node.get("reasoning").asText();
      return new LLMSuggestion(agentId, confidence, reasoning);
    } catch (Exception e) {
      log.warn("Failed to parse LLM response as JSON: {}", raw);
      throw new IllegalStateException("Unparseable LLM response", e);
    }
  }

  public LLMSuggestion parseAndValidate(String raw, java.util.List<Agent> eligibleAgents) {
    LLMSuggestion parsed = parse(raw);
    Set<String> validIds = eligibleAgents.stream().map(Agent::getId).collect(Collectors.toSet());
    if (!validIds.contains(parsed.agentId())) {
      throw new IllegalStateException(
          "LLM recommended unknown/ineligible agent id: " + parsed.agentId());
    }
    double clamped = Math.max(0.0, Math.min(1.0, parsed.confidence()));
    return new LLMSuggestion(parsed.agentId(), clamped, parsed.reasoning());
  }
}
