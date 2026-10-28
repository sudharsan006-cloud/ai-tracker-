package com.lifetracker.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HttpGeminiClient implements GeminiClient {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int maxOutputTokens;
    private final double temperature;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HttpGeminiClient(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-3.1-flash-lite}") String model,
            @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${app.gemini.connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${app.gemini.read-timeout-seconds:60}") int readTimeoutSeconds,
            @Value("${app.gemini.max-output-tokens:1024}") int maxOutputTokens,
            @Value("${app.gemini.temperature:0.3}") double temperature,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public GeminiResult generate(String systemInstruction, String userText) {
        if (apiKey == null || apiKey.isBlank()) {
            return new GeminiResult(0, null, null, null);
        }

        try {
            String url = baseUrl + "/models/" + model + ":generateContent";

            Map<String, Object> bodyMap = new HashMap<>();

            Map<String, Object> systemPart = Map.of("text", systemInstruction != null ? systemInstruction : "");
            bodyMap.put("systemInstruction", Map.of("parts", List.of(systemPart)));

            Map<String, Object> userPart = Map.of("text", userText != null ? userText : "");
            bodyMap.put("contents", List.of(Map.of("role", "user", "parts", List.of(userPart))));

            Map<String, Object> genConfig = Map.of(
                    "temperature", temperature,
                    "maxOutputTokens", maxOutputTokens
            );
            bodyMap.put("generationConfig", genConfig);

            String requestBody = objectMapper.writeValueAsString(bodyMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .timeout(Duration.ofSeconds(readTimeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status != 200) {
                String blockReason = null;
                String finishReason = null;
                try {
                    JsonNode errorNode = objectMapper.readTree(response.body());
                    if (errorNode.has("promptFeedback") && errorNode.get("promptFeedback").has("blockReason")) {
                        blockReason = errorNode.get("promptFeedback").get("blockReason").asText(null);
                    }
                    if (errorNode.has("candidates") && errorNode.get("candidates").isArray() && !errorNode.get("candidates").isEmpty()) {
                        finishReason = errorNode.get("candidates").get(0).path("finishReason").asText(null);
                    }
                } catch (Exception ignored) {
                }
                return new GeminiResult(status, null, blockReason, finishReason);
            }

            JsonNode root = objectMapper.readTree(response.body());
            String blockReason = null;
            if (root.has("promptFeedback") && root.get("promptFeedback").has("blockReason")) {
                blockReason = root.get("promptFeedback").get("blockReason").asText(null);
            }

            String finishReason = null;
            StringBuilder textBuilder = new StringBuilder();

            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                finishReason = firstCandidate.path("finishReason").asText(null);
                JsonNode parts = firstCandidate.path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            textBuilder.append(part.get("text").asText());
                        }
                    }
                }
            }

            String text = textBuilder.toString();
            return new GeminiResult(status, text, blockReason, finishReason);

        } catch (IOException | InterruptedException e) {
            return new GeminiResult(0, null, null, null);
        }
    }
}
