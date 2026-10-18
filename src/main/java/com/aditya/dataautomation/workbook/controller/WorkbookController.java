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
            ExcelWorkbookData workbook = workbookSessionHolder.get();
            String activeFileId = workbookSessionHolder.getActiveFileId();
            if (workbook == null) {
                Map<String, Object> emptyResp = new LinkedHashMap<>();
                emptyResp.put("loaded", false);
                emptyResp.put("canUndo", false);
                emptyResp.put("canRedo", false);
                emptyResp.put("fileId", null);
                emptyResp.put("files", workbookSessionHolder.listWorkbooks());
                return ResponseEntity.ok(emptyResp);
            }

            List<Map<String, Object>> sheetSummaries = new ArrayList<>();
            if (workbook.getSheets() != null) {
                for (ExcelSheetData sheet : workbook.getSheets()) {
                    if (sheet == null) continue;
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("sheetName", sheet.getSheetName() != null ? sheet.getSheetName() : "");
                    info.put("rowCount", sheet.getRowCount());
                    info.put("columnCount", sheet.getColumnCount());
                    info.put("headers", sheet.getHeaders() != null ? sheet.getHeaders() : Collections.emptyList());
                    sheetSummaries.add(info);
                }
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("loaded", true);
            response.put("fileId", activeFileId);
            response.put("fileName", workbook.getFileName() != null ? workbook.getFileName() : "Active Workbook");
            response.put("sheets", sheetSummaries);
            response.put("canUndo", workbookSessionHolder.canUndo());
            response.put("canRedo", workbookSessionHolder.canRedo());
            response.put("undoAction", workbookSessionHolder.getUndoAction() != null ? workbookSessionHolder.getUndoAction() : "");
            response.put("redoAction", workbookSessionHolder.getRedoAction() != null ? workbookSessionHolder.getRedoAction() : "");
            response.put("files", workbookSessionHolder.listWorkbooks());
            return ResponseEntity.ok(response);
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
        List<Map<String, Object>> preview = rows.subList(0, Math.min(limit, rows.size()));

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
    public ResponseEntity<Map<String, Object>> getAllData(@RequestParam String sheetName) {
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
        result.put("totalRows", sheet.getRowCount());
        result.put("rows", sheet.getRows());
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
        boolean selected = workbookSessionHolder.setActiveWorkbook(fileId);
        if (!selected) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("success", false);
            err.put("error", "File not found or access denied");
            return ResponseEntity.status(404).body(err);
        }
        ExcelWorkbookData active = workbookSessionHolder.get();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("fileId", fileId);
        resp.put("fileName", active != null ? active.getFileName() : "Active Workbook");
        return ResponseEntity.ok(resp);
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
