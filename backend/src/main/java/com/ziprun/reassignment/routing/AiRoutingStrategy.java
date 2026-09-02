package com.ziprun.reassignment.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.LLMSuggestion;
import com.ziprun.reassignment.ai.PromptBuilder;
import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI-backed routing strategy. Calls the LLM via {@link LLMGateway}, parses
 * and validates its JSON response, and throws on any failure mode (timeout,
 * quota, malformed JSON, hallucinated agent id) so the caller
 * ({@code ReassignmentService}) falls back to the rule-based strategy —
 * see ADR-3. This class never swallows a failure into a fabricated success.
 */
@Component("ai")
public class AiRoutingStrategy implements RoutingStrategy {

  private static final Logger log = LoggerFactory.getLogger(AiRoutingStrategy.class);

  private final LLMGateway llmGateway;
  private final PromptBuilder promptBuilder;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AiRoutingStrategy(LLMGateway llmGateway, PromptBuilder promptBuilder) {
    this.llmGateway = llmGateway;
    this.promptBuilder = promptBuilder;
  }

  @Override
  public List<AgentRecommendation> recommend(
      Order order, List<Agent> availableAgents, RoutingContext context) {
    if (availableAgents.isEmpty()) {
      throw new IllegalStateException("No available agents to recommend from");
    }

    String prompt = promptBuilder.build(order, availableAgents, context);
    String rawResponse = llmGateway.callLLM(prompt);
    LLMSuggestion parsed = parse(rawResponse);

    Set<String> validIds =
        availableAgents.stream().map(Agent::getId).collect(Collectors.toSet());
    if (!validIds.contains(parsed.agentId())) {
      throw new IllegalStateException(
          "LLM recommended unknown/unavailable agent id: " + parsed.agentId());
    }

    double confidence = Math.max(0.0, Math.min(1.0, parsed.confidence()));

    return List.of(new AgentRecommendation(parsed.agentId(), confidence, parsed.reasoning()));
  }

  private LLMSuggestion parse(String raw) {
    try {
      // LLMs sometimes wrap JSON in markdown fences despite instructions —
      // strip those defensively before parsing.
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
}
