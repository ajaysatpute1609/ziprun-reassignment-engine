package com.ziprun.reassignment.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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

  /**
   * Streams the LLM's response token-by-token via Server-Sent Events,
   * invoking {@code onToken} for each content fragment as it arrives.
   * Bonus feature (T-3): only implemented for the groq provider's
   * OpenAI-compatible streaming format ({@code "stream": true}).
   * Uses the JDK's HttpClient with a line-based body handler so lines are
   * delivered as the response streams in, rather than after the full
   * response is buffered.
   */
  public void streamLLM(String prompt, Consumer<String> onToken) throws IOException, InterruptedException {
    if (!"groq".equalsIgnoreCase(provider)) {
      throw new UnsupportedOperationException(
          "Streaming is only implemented for the groq provider (configured provider: " + provider + ")");
    }

    String url = baseUrl + "/openai/v1/chat/completions";
    Map<String, Object> body =
        Map.of(
            "model", model,
            "stream", true,
            "messages", List.of(Map.of("role", "user", "content", prompt)));

    ObjectMapper mapper = new ObjectMapper();
    String json;
    try {
      json = mapper.writeValueAsString(body);
    } catch (Exception e) {
      throw new IOException("Failed to serialize streaming request body", e);
    }

    HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis((long) timeoutMs * 4))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    HttpResponse<java.util.stream.Stream<String>> response =
        client.send(request, HttpResponse.BodyHandlers.ofLines());

    if (response.statusCode() >= 400) {
      throw new IOException("Groq streaming request failed with HTTP " + response.statusCode());
    }

    response
        .body()
        .forEach(
            line -> {
              if (!line.startsWith("data:")) {
                return;
              }
              String data = line.substring(5).trim();
              if (data.equals("[DONE]") || data.isEmpty()) {
                return;
              }
              try {
                JsonNode node = mapper.readTree(data);
                JsonNode choices = node.get("choices");
                if (choices != null && choices.size() > 0) {
                  JsonNode delta = choices.get(0).get("delta");
                  if (delta != null && delta.has("content")) {
                    String token = delta.get("content").asText();
                    if (!token.isEmpty()) {
                      onToken.accept(token);
                    }
                  }
                }
              } catch (Exception ignored) {
                // Malformed SSE chunk — skip it, don't abort the whole stream over one bad line.
              }
            });
  }

  private String callOpenAICompatible(String prompt, String url) {
    // response_format forces the model to return valid JSON, not prose.
    // This is especially important for the sync /suggest path used by both
    // manual calls and the proactive SlaMonitor (SLA_AT_RISK).
    var body =
        Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "response_format", Map.of("type", "json_object"));

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
