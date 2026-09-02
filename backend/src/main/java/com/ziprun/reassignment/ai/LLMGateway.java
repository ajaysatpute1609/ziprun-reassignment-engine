package com.ziprun.reassignment.ai;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Handles the provider-specific HTTP wire format (adapted from the brief's
 * Addendum B). Currently wired for Groq's OpenAI-compatible chat completions
 * endpoint; Gemini/Ollama branches kept for completeness in case the
 * provider is swapped via config.
 *
 * What this handles: authentication headers, request body shape, response
 * unwrapping. What remains the caller's job: prompt construction, JSON
 * parsing of the returned String, agent ID validation, and fallback logic.
 */
@Component
public class LLMGateway {

  @Value("${llm.provider}")
  private String provider;

  @Value("${llm.api-key:}")
  private String apiKey;

  @Value("${llm.model}")
  private String model;

  @Value("${llm.base-url}")
  private String baseUrl;

  @Value("${llm.timeout-ms:8000}")
  private int timeoutMs;

  private RestClient http;

  @jakarta.annotation.PostConstruct
  void init() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeoutMs);
    factory.setReadTimeout(timeoutMs);
    this.http = RestClient.builder().requestFactory(factory).build();
  }

  public String callLLM(String prompt) {
    return switch (provider.toLowerCase()) {
      case "gemini" -> callGemini(prompt);
      case "groq" -> callOpenAICompatible(prompt, baseUrl + "/openai/v1/chat/completions");
      case "ollama" -> callOpenAICompatible(prompt, baseUrl + "/v1/chat/completions");
      default -> throw new IllegalStateException("Unknown provider: " + provider);
    };
  }

  private String callGemini(String prompt) {
    var url = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
    var body =
        Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

    var resp = http.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);

    try {
      var candidates = (List<?>) resp.get("candidates");
      var content = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
      var parts = (List<?>) content.get("parts");
      return (String) ((Map<?, ?>) parts.get(0)).get("text");
    } catch (Exception e) {
      throw new RuntimeException("Gemini response parse failed", e);
    }
  }

  private String callOpenAICompatible(String prompt, String url) {
    var body =
        Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt)));

    var resp =
        http.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey)
            .body(body)
            .retrieve()
            .body(Map.class);

    try {
      var choices = (List<?>) resp.get("choices");
      var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
      return (String) message.get("content");
    } catch (Exception e) {
      throw new RuntimeException("LLM response parse failed", e);
    }
  }
}
