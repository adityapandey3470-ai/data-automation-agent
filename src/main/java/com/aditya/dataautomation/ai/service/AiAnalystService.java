package com.aditya.dataautomation.ai.service;

import com.aditya.dataautomation.admin.service.AiUsageService;
import com.aditya.dataautomation.ai.model.*;
import com.aditya.dataautomation.analysis.model.ColumnStatistics;
import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.aditya.dataautomation.exception.QuotaExceededException;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalystService {

    private final GeminiClientService geminiClient;
    private final DataInsightsService insightsService;
    private final WorkbookSessionHolder sessionHolder;
    private final AiUsageService aiUsageService;
    private final UserRepository userRepository;

    @Transactional
    public AiAnalystResponse ask(UserPrincipal principal, String question, String sheetName) {
        if (principal == null) {
            throw new InvalidRequestException("Authentication required. Please sign in to use the AI Analyst.");
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new InvalidRequestException("User account not found"));

        String tier = user.getSubscriptionTier() != null ? user.getSubscriptionTier().toUpperCase() : "FREE";
        int used = user.getAiQueriesUsed() != null ? user.getAiQueriesUsed() : 0;
        int limit = user.getAiQueriesLimit() != null ? user.getAiQueriesLimit() : 10;
        boolean isAdmin = "ROLE_ADMIN".equals(user.getRole());

        if (!isAdmin && !"ENTERPRISE".equals(tier) && used >= limit) {
            throw new QuotaExceededException("You have reached your limit of " + limit + " AI queries for your " + tier + " plan. Please upgrade to Pro for higher limits.");
        }

        long startTime = System.currentTimeMillis();

        ExcelWorkbookData workbook = sessionHolder.get();
        if (workbook == null) {
            return AiAnalystResponse.builder()
                    .status("UNAVAILABLE")
                    .engine("NONE")
                    .answer("No workbook is currently active in session. Please upload an Excel or CSV file in Data Workspace.")
                    .build();
        }

        ExcelSheetData sheet = resolveSheet(workbook, sheetName);
        if (sheet == null) {
            return AiAnalystResponse.builder()
                    .status("UNAVAILABLE")
                    .engine("NONE")
                    .answer("Sheet '" + sheetName + "' could not be found in active workbook.")
                    .build();
        }

        DataProfileResponse profile = insightsService.generateProfile(sheet);

        AiAnalystResponse response = null;
        int promptTokens = 0;
        int completionTokens = 0;

        if (geminiClient.isAvailable()) {
            try {
                List<Map<String, Object>> sample = sheet.getRows() != null && !sheet.getRows().isEmpty()
                        ? sheet.getRows().subList(0, Math.min(2, sheet.getRows().size()))
                        : Collections.emptyList();

                JsonNode aiResult = geminiClient.askAnalyst(question, profile, sample);
                if (aiResult != null && aiResult.has("answer")) {
                    response = parseAiResult(aiResult, profile, startTime, question);
                    promptTokens = Math.max(90, (question.length() + 850) / 4);
                    completionTokens = Math.max(60, response.getAnswer().length() / 4);
                }
            } catch (Exception e) {
                log.warn("Gemini execution error, reverting to deterministic fallback: {}", e.getMessage());
            }
        }

        if (response == null) {
            response = buildDeterministicFallback(question, profile, startTime);
            promptTokens = Math.max(60, (question.length() + 300) / 4);
            completionTokens = Math.max(40, response.getAnswer().length() / 4);
        }

        int totalTokens = promptTokens + completionTokens;
        long latency = Math.max(1, System.currentTimeMillis() - startTime);

        aiUsageService.recordUsage(
                user.getId(),
                user.getEmail(),
                "AI_ANALYST",
                response.getModel() != null ? response.getModel() : geminiClient.getModelName(),
                promptTokens,
                completionTokens,
                latency,
                response.getStatus(),
                question
        );

        int updatedUsed = used + 1;
        long updatedTokens = (user.getTotalTokensUsed() != null ? user.getTotalTokensUsed() : 0L) + totalTokens;
        user.setAiQueriesUsed(updatedUsed);
        user.setTotalTokensUsed(updatedTokens);
        userRepository.save(user);

        response.setSubscriptionTier(tier);
        response.setQueriesUsed(updatedUsed);
        response.setQueriesLimit(limit);
        response.setRemainingQueries(isAdmin || "ENTERPRISE".equals(tier) ? 999999 : Math.max(0, limit - updatedUsed));
        response.setTokensConsumed(totalTokens);
        response.setLatencyMs(latency);

        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getUserQuota(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("User account not found"));

        String tier = user.getSubscriptionTier() != null ? user.getSubscriptionTier().toUpperCase() : "FREE";
        int used = user.getAiQueriesUsed() != null ? user.getAiQueriesUsed() : 0;
        int limit = user.getAiQueriesLimit() != null ? user.getAiQueriesLimit() : 10;
        boolean isAdmin = "ROLE_ADMIN".equals(user.getRole());
        int remaining = isAdmin || "ENTERPRISE".equals(tier) ? 999999 : Math.max(0, limit - used);
        boolean reached = !isAdmin && !"ENTERPRISE".equals(tier) && used >= limit;

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("subscriptionTier", tier);
        map.put("queriesUsed", used);
        map.put("queriesLimit", limit);
        map.put("remainingQueries", remaining);
        map.put("totalTokensUsed", user.getTotalTokensUsed() != null ? user.getTotalTokensUsed() : 0L);
        map.put("isLimitReached", reached);
        map.put("isAdmin", isAdmin);
        return map;
    }

    private AiAnalystResponse parseAiResult(JsonNode root, DataProfileResponse profile, long startTime, String question) {
        String answer = root.path("answer").asText("Analysis completed.");

        List<DataInsight> insights = new ArrayList<>();
        JsonNode insightsNode = root.path("insights");
        if (insightsNode.isArray()) {
            int idx = 1;
            for (JsonNode n : insightsNode) {
                insights.add(DataInsight.builder()
                        .id("AI-INS-" + idx++)
                        .title(n.path("title").asText("Insight"))
                        .statisticalFact(n.path("statisticalFact").asText(""))
                        .interpretation(n.path("interpretation").asText(""))
                        .category(n.path("category").asText("ANALYSIS"))
                        .severity(n.path("severity").asText("INFO"))
                        .build());
            }
        }

        List<AiSuggestedAction> actions = new ArrayList<>();
        JsonNode actionsNode = root.path("suggestedActions");
        if (actionsNode.isArray()) {
            for (JsonNode a : actionsNode) {
                String cmd = a.path("command").asText("");
                boolean isDestructive = cmd.toLowerCase().contains("remove") || cmd.toLowerCase().contains("drop") || cmd.toLowerCase().contains("clean");
                actions.add(AiSuggestedAction.builder()
                        .label(a.path("label").asText("Execute Action"))
                        .command(cmd)
                        .requiresConfirmation(isDestructive || a.path("requiresConfirmation").asBoolean(true))
                        .build());
            }
        }

        List<String> followUps = new ArrayList<>();
        JsonNode followUpsNode = root.path("suggestedFollowUps");
        if (followUpsNode.isArray()) {
            for (JsonNode f : followUpsNode) {
                followUps.add(f.asText());
            }
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalRows", profile.getTotalRows());
        evidence.put("totalColumns", profile.getTotalColumns());
        evidence.put("completeness", profile.getCompletenessPercentage() + "%");
        evidence.put("duplicates", profile.getDuplicateRowsCount());

        return AiAnalystResponse.builder()
                .status("SUCCESS")
                .engine("GEMINI_AI")
                .model(geminiClient.getModelName())
                .answer(answer)
                .insights(insights)
                .verifiedEvidence(evidence)
                .suggestedActions(actions)
                .suggestedFollowUps(followUps.isEmpty() ? profile.getSuggestedQuestions() : followUps)
                .build();
    }

    private AiAnalystResponse buildDeterministicFallback(String question, DataProfileResponse profile, long startTime) {
        String q = question.toLowerCase();
        StringBuilder answer = new StringBuilder();
        List<DataInsight> relevantInsights = new ArrayList<>();
        List<AiSuggestedAction> actions = new ArrayList<>();

        if (q.contains("duplicate") || q.contains("dedup")) {
            if (profile.getDuplicateRowsCount() > 0) {
                answer.append(String.format("Found %d exact duplicate rows (%.1f%% of all records). ",
                        profile.getDuplicateRowsCount(), profile.getDuplicatePercentage()));
                answer.append("Removing duplicates will improve accuracy across aggregate metrics.");
                actions.add(AiSuggestedAction.builder()
                        .label("Remove Duplicates (Keep First)")
                        .command("remove duplicates")
                        .requiresConfirmation(true)
                        .build());
            } else {
                answer.append("No exact duplicate rows were detected in this sheet.");
            }
        } else if (q.contains("missing") || q.contains("null") || q.contains("empty")) {
            if (profile.getMissingCells() > 0) {
                answer.append(String.format("Detected %d missing cells across dataset (overall completeness: %.1f%%). ",
                        profile.getMissingCells(), profile.getCompletenessPercentage()));
                answer.append("Review columns with highest null rates to determine appropriate fill or drop strategies.");
            } else {
                answer.append("Dataset is 100% complete with no empty or null cells detected.");
            }
        } else if (q.contains("correlation") || q.contains("relationship") || q.contains("trend")) {
            int statCols = profile.getColumnStatistics() != null ? profile.getColumnStatistics().size() : 0;
            answer.append(String.format("Sheet contains %d analyzed columns. ", statCols));
            if (!profile.getInsights().isEmpty()) {
                answer.append("Key statistical discovery: ").append(profile.getInsights().get(0).getStatisticalFact()).append(" ");
                answer.append(profile.getInsights().get(0).getInterpretation());
                relevantInsights.add(profile.getInsights().get(0));
            }
        } else {
            answer.append(String.format("Workbook overview for '%s': %d records with %d columns. ",
                    profile.getSheetName(), profile.getTotalRows(), profile.getTotalColumns()));
            if (!profile.getInsights().isEmpty()) {
                answer.append(profile.getInsights().get(0).getStatisticalFact()).append(". ");
                relevantInsights.addAll(profile.getInsights().subList(0, Math.min(3, profile.getInsights().size())));
            }
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("totalRows", profile.getTotalRows());
        evidence.put("totalColumns", profile.getTotalColumns());
        evidence.put("completeness", profile.getCompletenessPercentage() + "%");
        evidence.put("duplicates", profile.getDuplicateRowsCount());

        return AiAnalystResponse.builder()
                .status("FALLBACK")
                .engine("DETERMINISTIC_FALLBACK")
                .model("Tabufinery Deterministic Engine")
                .answer(answer.toString())
                .insights(relevantInsights.isEmpty() ? profile.getInsights() : relevantInsights)
                .verifiedEvidence(evidence)
                .suggestedActions(actions)
                .suggestedFollowUps(profile.getSuggestedQuestions())
                .build();
    }

    private ExcelSheetData resolveSheet(ExcelWorkbookData workbook, String sheetName) {
        if (sheetName != null && !sheetName.isBlank()) {
            ExcelSheetData sheet = workbook.getSheet(sheetName);
            if (sheet != null) return sheet;
        }
        return workbook.getSheets() != null && !workbook.getSheets().isEmpty()
                ? workbook.getSheets().get(0)
                : null;
    }
}
