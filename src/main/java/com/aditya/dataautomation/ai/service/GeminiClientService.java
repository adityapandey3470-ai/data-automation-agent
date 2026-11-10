package com.aditya.dataautomation.ai.service;

import com.aditya.dataautomation.ai.model.DataProfileResponse;
import com.aditya.dataautomation.config.properties.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class GeminiClientService {

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** Opens after 5 failures in a row; callers then use their fallback instantly instead of waiting for the timeout. */
    private final com.aditya.dataautomation.common.resilience.SimpleCircuitBreaker breaker =
            new com.aditya.dataautomation.common.resilience.SimpleCircuitBreaker(5, 60_000);

    /** At most 10 Gemini calls at the same time, so a slow Gemini cannot use up all web server threads. */
    private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(10);

    private static boolean isTransient(Exception e) {
        if (e instanceof org.springframework.web.client.RestClientResponseException r) {
            int s = r.getStatusCode().value();
            return s == 429 || s >= 500;
        }
        return true; // timeouts, connection refused, I/O errors
    }

    public GeminiClientService(
            GeminiProperties geminiProperties,
            ObjectMapper objectMapper) {

        String key = geminiProperties.getApiKey();
        if (key == null || key.isBlank()) {
            key = System.getenv("GEMINI_API_KEY");
        }

        this.apiKey = key != null ? key.trim() : "";
        this.model = geminiProperties.getModel() != null && !geminiProperties.getModel().isBlank()
                ? geminiProperties.getModel().trim() : "gemini-2.5-flash";
        this.timeoutSeconds = geminiProperties.getTimeoutSeconds() > 0 ? geminiProperties.getTimeoutSeconds() : 15;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(this.timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(this.timeoutSeconds));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();

        if (isAvailable()) {
            log.info("Gemini AI Client initialized with model: {}", this.model);
        } else {
            log.info("Gemini AI API key not configured. Automatic fallback to deterministic analysis engine.");
        }
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getModelName() {
        return this.model;
    }

    public String askSupport(String userQuestion, String systemPrompt, List<Map<String, String>> conversationHistory) {
        if (!isAvailable()) {
            return null;
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
                this.model);

        List<Map<String, Object>> contents = new ArrayList<>();
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append(systemPrompt).append("\n\n");
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            promptBuilder.append("Recent conversation context:\n");
            for (Map<String, String> msg : conversationHistory) {
                String role = "user".equalsIgnoreCase(msg.get("role")) ? "User" : "Assistant";
                promptBuilder.append(role).append(": ").append(msg.get("text")).append("\n");
            }
            promptBuilder.append("\n");
        }
        promptBuilder.append("Current User Inquiry: ").append(userQuestion);

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", promptBuilder.toString()))
        ));

        Map<String, Object> requestPayload = Map.of(
                "contents", contents,
                "generationConfig", Map.of(
                        "temperature", 0.3,
                        "maxOutputTokens", 1024
                )
        );

        if (!breaker.allowRequest() || !slots.tryAcquire()) {
            return null; // fallback right away
        }

        try {
            String responseStr = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", this.apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            breaker.recordSuccess();

            if (responseStr == null || responseStr.isBlank()) {
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(responseStr);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText("");
                }
            }
            return null;
        } catch (Exception e) {
            if (isTransient(e)) {
                breaker.recordFailure();
            }
            log.warn("Gemini support chat request failed: {}. Falling back to knowledge base.", e.getMessage());
            return null;
        } finally {
            slots.release();
        }
    }

    public JsonNode askAnalyst(String userQuestion, DataProfileResponse profile, List<Map<String, Object>> sampleRows) {
        if (!isAvailable()) {
            return null;
        }
        if (!breaker.allowRequest() || !slots.tryAcquire()) {
            return null; // fallback right away
        }

        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent",
                this.model);

        String systemPrompt = buildSystemPrompt(profile, sampleRows);

        Map<String, Object> requestPayload = Map.of(
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", systemPrompt + "\n\nUser Question: " + userQuestion)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 2048,
                        "responseMimeType", "application/json"
                )
        );

        try {
            String responseStr = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", this.apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            breaker.recordSuccess();

            if (responseStr == null || responseStr.isBlank()) {
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(responseStr);
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    String textContent = parts.get(0).path("text").asText("");
                    JsonNode parsedAnswer = objectMapper.readTree(textContent);
                    JsonNode usage = rootNode.path("usageMetadata");
                    if (parsedAnswer instanceof com.fasterxml.jackson.databind.node.ObjectNode on && usage.isObject()) {
                        on.put("_promptTokens", usage.path("promptTokenCount").asInt(0));
                        on.put("_completionTokens", usage.path("candidatesTokenCount").asInt(0));
                    }
                    return parsedAnswer;
                }
            }

            return null;
        } catch (Exception e) {
            if (isTransient(e)) {
                breaker.recordFailure();
            }
            log.warn("Gemini API request failed or timed out: {}. Using deterministic fallback.", e.getMessage());
            return null;
        } finally {
            slots.release();
        }
    }

    private String buildSystemPrompt(DataProfileResponse profile, List<Map<String, Object>> sampleRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Tabufinery's Senior Enterprise AI Data Analyst.\n");
        sb.append("Analyze the user's spreadsheet workbook based on the STRICT verified summary statistics provided below.\n");
        sb.append("RULES:\n");
        sb.append("1. Answer accurately using only verified numbers from the summary. Never fabricate metrics.\n");
        sb.append("2. Distinguish statistical facts from business interpretations.\n");
        sb.append("3. Return a clean JSON response adhering to the exact schema specified below.\n");
        sb.append("4. Suggest clear, deterministic action commands for the user where appropriate (e.g. 'remove duplicates', 'filter <col> equals <val>', 'sort <col> desc').\n\n");

        sb.append("--- VERIFIED DATASET PROFILE ---\n");
        sb.append(String.format("Sheet Name: %s\n", profile.getSheetName()));
        sb.append(String.format("Total Records: %d rows, %d columns\n", profile.getTotalRows(), profile.getTotalColumns()));
        sb.append(String.format("Data Completeness: %.1f%% (Missing Cells: %d)\n", profile.getCompletenessPercentage(), profile.getMissingCells()));
        sb.append(String.format("Duplicates: %d identical rows (%.1f%%)\n", profile.getDuplicateRowsCount(), profile.getDuplicatePercentage()));
        sb.append(String.format("Headers: %s\n", String.join(", ", profile.getHeaders())));

        sb.append("\nCOLUMN PROFILES:\n");
        if (profile.getColumnStatistics() != null) {
            profile.getColumnStatistics().forEach((col, stats) -> {
                sb.append(String.format("• %s [%s] — Count: %d, Nulls: %d, Distinct: %d",
                        col, stats.getDataType(), stats.getCount(), stats.getNullCount(), stats.getDistinctCount()));
                if (stats.getAverage() != null) {
                    sb.append(String.format(", Avg: %s, Min: %s, Max: %s, Sum: %s",
                            stats.getAverage(), stats.getMin(), stats.getMax(), stats.getSum()));
                }
                if (stats.getTopValues() != null && !stats.getTopValues().isEmpty()) {
                    sb.append(", Top Categories: ").append(stats.getTopValues().subList(0, Math.min(3, stats.getTopValues().size())));
                }
                sb.append("\n");
            });
        }

        if (sampleRows != null && !sampleRows.isEmpty()) {
            sb.append("\nSAMPLE ROW SNIPPET (First 2 rows):\n");
            int rowIdx = 1;
            for (Map<String, Object> row : sampleRows.subList(0, Math.min(2, sampleRows.size()))) {
                sb.append("Row ").append(rowIdx++).append(": {");
                List<String> entries = new ArrayList<>();
                for (String h : profile.getHeaders()) {
                    Object val = row.get(h);
                    String valStr = val != null ? val.toString() : "null";
                    if (valStr.length() > 60) valStr = valStr.substring(0, 57) + "...";
                    entries.add(h + ": " + valStr);
                }
                sb.append(String.join(", ", entries)).append("}\n");
            }
        }

        sb.append("\nOUTPUT JSON FORMAT:\n");
        sb.append("{\n");
        sb.append("  \"answer\": \"Detailed clear answer to the user's question.\",\n");
        sb.append("  \"insights\": [\n");
        sb.append("    {\n");
        sb.append("      \"title\": \"Finding Title\",\n");
        sb.append("      \"statisticalFact\": \"Exact metric from summary\",\n");
        sb.append("      \"interpretation\": \"Potential business meaning\",\n");
        sb.append("      \"category\": \"QUALITY | DISTRIBUTION | OUTLIER | OPPORTUNITY\",\n");
        sb.append("      \"severity\": \"INFO | WARNING | SUCCESS\"\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"suggestedActions\": [\n");
        sb.append("    {\n");
        sb.append("      \"label\": \"Human readable label\",\n");
        sb.append("      \"command\": \"Deterministic action command\",\n");
        sb.append("      \"requiresConfirmation\": true\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"suggestedFollowUps\": [\"Question 1?\", \"Question 2?\"]\n");
        sb.append("}\n");

        return sb.toString();
    }
}
