package com.aditya.dataautomation.ai.controller;

import com.aditya.dataautomation.ai.model.AiAnalystRequest;
import com.aditya.dataautomation.ai.model.AiAnalystResponse;
import com.aditya.dataautomation.ai.model.DataProfileResponse;
import com.aditya.dataautomation.ai.service.AiAnalystService;
import com.aditya.dataautomation.ai.service.DataInsightsService;
import com.aditya.dataautomation.ai.service.GeminiClientService;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Analyst & Insights", description = "Automated data insights and natural language AI analyst")
public class AiAnalystController {

    private final AiAnalystService analystService;
    private final DataInsightsService insightsService;
    private final GeminiClientService geminiClient;
    private final WorkbookSessionHolder sessionHolder;
    private final com.aditya.dataautomation.idempotency.IdempotencyService idempotencyService;

    @GetMapping("/status")
    @Operation(summary = "Check AI Engine status", description = "Returns whether Gemini AI is configured and ready")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "available", geminiClient.isAvailable(),
                "model", geminiClient.getModelName(),
                "engine", geminiClient.isAvailable() ? "GEMINI_AI" : "DETERMINISTIC_FALLBACK"
        ));
    }

    @GetMapping("/quota")
    @Operation(summary = "Get user's AI quota and subscription tier status")
    public ResponseEntity<Map<String, Object>> getQuota(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(analystService.getUserQuota(principal.getId()));
    }

    @GetMapping("/profile")
    @Operation(summary = "Generate automatic data insights profile",
            description = "Analyzes active workbook and returns comprehensive data profile, statistical facts, and insights")
    public ResponseEntity<DataProfileResponse> getProfile(@RequestParam(required = false) String sheetName) {
        ExcelWorkbookData workbook = sessionHolder.get();
        if (workbook == null) {
            return ResponseEntity.badRequest().build();
        }

        ExcelSheetData sheet = resolveSheet(workbook, sheetName);
        if (sheet == null) {
            return ResponseEntity.badRequest().build();
        }

        DataProfileResponse profile = insightsService.generateProfile(sheet);
        return ResponseEntity.ok(profile);
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask the AI Analyst a question",
            description = "Analyzes question with Gemini AI (or deterministic engine) using verified dataset profile")
    public ResponseEntity<?> ask(
            @AuthenticationPrincipal UserPrincipal principal,
            @org.springframework.web.bind.annotation.RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AiAnalystRequest request) {
        Object response = idempotencyService.execute(principal != null ? principal.getId() : null, "ai-ask", idempotencyKey,
                () -> analystService.ask(principal, request.getQuestion(), request.getSheetName()));
        return ResponseEntity.ok(response);
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
