package com.aditya.dataautomation.ai.service;

import com.aditya.dataautomation.ai.model.*;
import com.aditya.dataautomation.analysis.model.ColumnStatistics;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalystService {

    private final GeminiClientService geminiClient;
    private final DataInsightsService insightsService;
    private final WorkbookSessionHolder sessionHolder;

    public AiAnalystResponse ask(String question, String sheetName) {
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

        // 1. Compute verified profile first
        DataProfileResponse profile = insightsService.generateProfile(sheet);

        // 2. Try Gemini AI if available
        if (geminiClient.isAvailable()) {
            try {
                List<Map<String, Object>> sample = sheet.getRows() != null && !sheet.getRows().isEmpty()
                        ? sheet.getRows().subList(0, Math.min(2, sheet.getRows().size()))
                        : Collections.emptyList();

                JsonNode aiResult = geminiClient.askAnalyst(question, profile, sample);
                if (aiResult != null && aiResult.has("answer")) {
                    return parseAiResult(aiResult, profile, startTime);
                }
            } catch (Exception e) {
                log.warn("Gemini execution error, reverting to deterministic fallback: {}", e.getMessage());
            }
        }

        // 3. Fallback: Deterministic Analytics Engine
        return buildDeterministicFallback(question, profile, startTime);
    }

    private AiAnalystResponse parseAiResult(JsonNode root, DataProfileResponse profile, long startTime) {
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
                .latencyMs(System.currentTimeMillis() - startTime)
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
                answer.append("Verified duplicate check: Zero duplicate rows were found in this dataset.");
            }
        } else if (q.contains("missing") || q.contains("null") || q.contains("empty")) {
            answer.append(String.format("Dataset has %.1f%% completeness with %d missing cells across %d rows.",
                    profile.getCompletenessPercentage(), profile.getMissingCells(), profile.getTotalRows()));
            actions.add(AiSuggestedAction.builder()
                    .label("Inspect Missing Values")
                    .command("find missing values")
                    .requiresConfirmation(false)
                    .build());
        } else if (q.contains("outlier") || q.contains("anomaly") || q.contains("extreme")) {
            for (DataInsight ins : profile.getInsights()) {
                if ("OUTLIER".equals(ins.getCategory())) {
                    relevantInsights.add(ins);
                }
            }
            if (relevantInsights.isEmpty()) {
                answer.append("No severe outliers (> 2.8 standard deviations) were detected in numeric columns.");
            } else {
                answer.append(String.format("Detected %d statistical outlier patterns in numeric fields.", relevantInsights.size()));
            }
        } else {
            answer.append(String.format("Dataset '%s' contains %d rows and %d columns (%s). Overall completeness is %.1f%% with %d duplicate records.",
                    profile.getSheetName(), profile.getTotalRows(), profile.getTotalColumns(),
                    String.join(", ", profile.getHeaders()), profile.getCompletenessPercentage(), profile.getDuplicateRowsCount()));
            relevantInsights = profile.getInsights();
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
                .latencyMs(System.currentTimeMillis() - startTime)
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
