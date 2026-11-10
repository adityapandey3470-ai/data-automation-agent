package com.aditya.dataautomation.workbook.controller;

import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/workbook")
@RequiredArgsConstructor
@Tag(name = "Workbook", description = "Query and manage workbook states and session history")
public class WorkbookController {

    private final WorkbookSessionHolder workbookSessionHolder;

    @GetMapping("/status")
    @Operation(summary = "Check if a workbook is loaded and get history state")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            return ResponseEntity.ok(workbookSessionHolder.getStatusMap());
        } catch (Exception ex) {
            log.error("Error retrieving workbook status: {}", ex.getMessage(), ex);
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("loaded", false);
            fallback.put("canUndo", false);
            fallback.put("canRedo", false);
            fallback.put("fileId", null);
            fallback.put("files", Collections.emptyList());
            return ResponseEntity.ok(fallback);
        }
    }

    @GetMapping("/preview")
    @Operation(summary = "Preview data from a sheet",
            description = "Returns up to 'limit' rows from the specified sheet")
    public ResponseEntity<Map<String, Object>> previewData(
            @RequestParam String sheetName,
            @RequestParam(defaultValue = "50") int limit) {
        ExcelWorkbookData workbook = workbookSessionHolder.get();
        if (workbook == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No workbook loaded"));
        }
        ExcelSheetData sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sheet not found: " + sheetName));
        }

        List<Map<String, Object>> rows = sheet.getRows();
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        List<Map<String, Object>> preview = rows.subList(0, Math.min(safeLimit, rows.size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sheetName", sheet.getSheetName());
        result.put("headers", sheet.getHeaders());
        result.put("totalRows", rows.size());
        result.put("previewRows", preview.size());
        result.put("rows", preview);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/data")
    @Operation(summary = "Get all data from a sheet")
    public ResponseEntity<Map<String, Object>> getAllData(
            @RequestParam String sheetName,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100000") int limit) {
        ExcelWorkbookData workbook = workbookSessionHolder.get();
        if (workbook == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "No workbook loaded"));
        }
        ExcelSheetData sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Sheet not found: " + sheetName));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sheetName", sheet.getSheetName());
        result.put("headers", sheet.getHeaders());
        List<Map<String, Object>> all = sheet.getRows() != null ? sheet.getRows() : List.of();
        int from = Math.max(0, Math.min(offset, all.size()));
        int to = Math.min(all.size(), from + Math.max(1, Math.min(limit, 100000)));
        result.put("totalRows", sheet.getRowCount());
        result.put("offset", from);
        result.put("returnedRows", to - from);
        result.put("truncated", to < all.size());
        result.put("rows", all.subList(from, to));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/undo")
    @Operation(summary = "Undo last workbook change",
            description = "Restores the previous workbook state in the current session.")
    public ResponseEntity<Map<String, Object>> undo() {
        if (!workbookSessionHolder.canUndo()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Nothing to undo");
            return ResponseEntity.badRequest().body(err);
        }

        WorkbookSessionHolder.HistoryState restored = workbookSessionHolder.undo();
        String desc = (restored != null && restored.getActionDescription() != null)
                ? restored.getActionDescription()
                : "Previous State";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Undid: " + desc);
        response.put("action", desc);
        response.put("canUndo", workbookSessionHolder.canUndo());
        response.put("canRedo", workbookSessionHolder.canRedo());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/redo")
    @Operation(summary = "Redo previously undone workbook change",
            description = "Re-applies the next workbook state in the current session.")
    public ResponseEntity<Map<String, Object>> redo() {
        if (!workbookSessionHolder.canRedo()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("message", "Nothing to redo");
            return ResponseEntity.badRequest().body(err);
        }

        WorkbookSessionHolder.HistoryState restored = workbookSessionHolder.redo();
        String desc = (restored != null && restored.getActionDescription() != null)
                ? restored.getActionDescription()
                : "Next State";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "Redone: " + desc);
        response.put("action", desc);
        response.put("canUndo", workbookSessionHolder.canUndo());
        response.put("canRedo", workbookSessionHolder.canRedo());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get workbook undo/redo status and history")
    public ResponseEntity<Map<String, Object>> getHistory() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("canUndo", workbookSessionHolder.canUndo());
        response.put("canRedo", workbookSessionHolder.canRedo());
        response.put("undoAction", workbookSessionHolder.getUndoAction() != null ? workbookSessionHolder.getUndoAction() : "");
        response.put("redoAction", workbookSessionHolder.getRedoAction() != null ? workbookSessionHolder.getRedoAction() : "");
        response.put("history", workbookSessionHolder.getHistoryDescriptions() != null ? workbookSessionHolder.getHistoryDescriptions() : Collections.emptyList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/files")
    @Operation(summary = "List all uploaded workbooks in the current user's workspace")
    public ResponseEntity<List<com.aditya.dataautomation.excel.dto.WorkbookSummaryDto>> listFiles() {
        return ResponseEntity.ok(workbookSessionHolder.listWorkbooks());
    }

    @PostMapping("/files/{fileId}/select")
    @Operation(summary = "Switch the active workbook in the current user's workspace")
    public ResponseEntity<Map<String, Object>> selectFile(@PathVariable String fileId) {
        Map<String, Object> status = workbookSessionHolder.selectAndGetStatus(fileId);
        if (status == null) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "File not found or access denied");
            return ResponseEntity.status(404).body(err);
        }
        status.put("success", true);
        status.put("fileId", fileId);
        return ResponseEntity.ok(status);
    }

    @DeleteMapping("/files/{fileId}")
    @Operation(summary = "Delete an uploaded workbook from the current user's workspace")
    public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable String fileId) {
        boolean deleted = workbookSessionHolder.deleteWorkbook(fileId);
        if (!deleted) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "File not found or access denied");
            return ResponseEntity.status(404).body(err);
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("message", "File removed from workspace");
        resp.put("activeFileId", workbookSessionHolder.getActiveFileId());
        return ResponseEntity.ok(resp);
    }
}
