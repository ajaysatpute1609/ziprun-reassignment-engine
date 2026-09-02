package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.LLMResponseParser;
import com.ziprun.reassignment.ai.LLMSuggestion;
import com.ziprun.reassignment.ai.PromptBuilder;
import com.ziprun.reassignment.domain.Agent;
import com.ziprun.reassignment.domain.Order;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI-backed routing strategy. Calls the LLM via {@link LLMGateway}, parses
 * and validates its JSON response via the shared {@link LLMResponseParser},
 * and throws on any failure mode (timeout, quota, malformed JSON,
 * hallucinated agent id) so the caller ({@code ReassignmentService}) falls
 * back to the rule-based strategy — see ADR-3. This class never swallows a
 * failure into a fabricated success.
 */
@Component("ai")
public class AiRoutingStrategy implements RoutingStrategy {

  private final LLMGateway llmGateway;
  private final PromptBuilder promptBuilder;
  private final LLMResponseParser responseParser;

  public AiRoutingStrategy(
      LLMGateway llmGateway, PromptBuilder promptBuilder, LLMResponseParser responseParser) {
    this.llmGateway = llmGateway;
    this.promptBuilder = promptBuilder;
    this.responseParser = responseParser;
  }

  @Override
  public List<AgentRecommendation> recommend(
      Order order, List<Agent> availableAgents, RoutingContext context) {
    if (availableAgents.isEmpty()) {
      throw new IllegalStateException("No available agents to recommend from");
    }

    String prompt = promptBuilder.build(order, availableAgents, context);
    String rawResponse = llmGateway.callLLM(prompt);
    LLMSuggestion validated = responseParser.parseAndValidate(rawResponse, availableAgents);

    return List.of(
        new AgentRecommendation(
            validated.agentId(), validated.confidence(), validated.reasoning()));
  }
}
