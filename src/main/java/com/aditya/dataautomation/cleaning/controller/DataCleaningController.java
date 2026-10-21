package com.aditya.dataautomation.cleaning.controller;

import com.aditya.dataautomation.cleaning.dto.ChangePreviewResponse;
import com.aditya.dataautomation.cleaning.exception.CleaningException;
import com.aditya.dataautomation.cleaning.model.*;
import com.aditya.dataautomation.cleaning.service.DataCleaningService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cleaning")
@RequiredArgsConstructor
@Tag(name = "Data Cleaning", description = "Clean and transform uploaded Excel data with preview & history support")
public class DataCleaningController {

    private final DataCleaningService cleaningService;
    private final WorkbookSessionHolder workbookSessionHolder;

    @PostMapping("/preview")
    @Operation(summary = "Preview cleaning operation without applying changes",
            description = "Simulates the transformation and returns before/after rows, affected column names, and row/cell change metrics. Does not mutate the session workbook.")
    public ResponseEntity<ChangePreviewResponse> previewCleaning(@RequestBody TransformationRequest request) {
        ExcelWorkbookData workbook = getWorkbookOrThrow();
        CleaningResult result = cleaningService.execute(workbook, request);

        ExcelSheetData beforeSheet = workbook.getSheet(request.getSheetName());
        ExcelSheetData afterSheet = result.getCleanedWorkbook().getSheet(result.getAffectedSheet());

        List<Map<String, Object>> beforeRows = beforeSheet != null && beforeSheet.getRows() != null
                ? beforeSheet.getRows().subList(0, Math.min(20, beforeSheet.getRows().size()))
                : Collections.emptyList();

        List<Map<String, Object>> afterRows = afterSheet != null && afterSheet.getRows() != null
                ? afterSheet.getRows().subList(0, Math.min(20, afterSheet.getRows().size()))
                : Collections.emptyList();

        List<String> affectedCols = new ArrayList<>();
        if (request.getColumnName() != null && !request.getColumnName().isBlank()) {
            affectedCols.add(request.getColumnName());
        }
        if (request.getColumns() != null) {
            affectedCols.addAll(request.getColumns());
        }

        ChangePreviewResponse preview = ChangePreviewResponse.builder()
                .operation(result.getOperation())
                .sheetName(result.getAffectedSheet())
                .rowsBefore(result.getRowsBefore())
                .rowsAfter(result.getRowsAfter())
                .rowsRemoved(result.getRowsRemoved() != null ? result.getRowsRemoved() : Math.max(0, result.getRowsBefore() - result.getRowsAfter()))
                .cellsChanged(result.getCellsChanged() != null ? result.getCellsChanged() : 0)
                .columnCountBefore(beforeSheet != null ? beforeSheet.getColumnCount() : 0)
                .columnCountAfter(afterSheet != null ? afterSheet.getColumnCount() : 0)
                .headersBefore(beforeSheet != null ? beforeSheet.getHeaders() : Collections.emptyList())
                .headersAfter(afterSheet != null ? afterSheet.getHeaders() : Collections.emptyList())
                .beforeRows(beforeRows)
                .afterRows(afterRows)
                .affectedColumns(affectedCols)
                .summary(result.getMessage())
                .build();

        return ResponseEntity.ok(preview);
    }

    @PostMapping("/preview-pipeline")
    @Operation(summary = "Preview batch pipeline without applying changes",
            description = "Simulates sequential pipeline transformations and returns overall before/after rows and change metrics.")
    public ResponseEntity<ChangePreviewResponse> previewPipeline(@RequestBody List<TransformationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new CleaningException("Pipeline requests cannot be empty");
        }
        ExcelWorkbookData workbook = getWorkbookOrThrow();
        List<CleaningResult> results = cleaningService.executePipeline(workbook, requests);
        CleaningResult lastResult = results.get(results.size() - 1);

        String sheetName = requests.get(0).getSheetName();
        ExcelSheetData beforeSheet = workbook.getSheet(sheetName);
        ExcelSheetData afterSheet = lastResult.getCleanedWorkbook().getSheet(lastResult.getAffectedSheet());

        List<Map<String, Object>> beforeRows = beforeSheet != null && beforeSheet.getRows() != null
                ? beforeSheet.getRows().subList(0, Math.min(20, beforeSheet.getRows().size()))
                : Collections.emptyList();

        List<Map<String, Object>> afterRows = afterSheet != null && afterSheet.getRows() != null
                ? afterSheet.getRows().subList(0, Math.min(20, afterSheet.getRows().size()))
                : Collections.emptyList();

        ChangePreviewResponse preview = ChangePreviewResponse.builder()
                .operation("BATCH_PIPELINE")
                .sheetName(lastResult.getAffectedSheet())
                .rowsBefore(results.get(0).getRowsBefore())
                .rowsAfter(lastResult.getRowsAfter())
                .rowsRemoved(results.get(0).getRowsBefore() - lastResult.getRowsAfter())
                .cellsChanged(results.stream().mapToInt(r -> r.getCellsChanged() != null ? r.getCellsChanged() : 0).sum())
                .columnCountBefore(beforeSheet != null ? beforeSheet.getColumnCount() : 0)
                .columnCountAfter(afterSheet != null ? afterSheet.getColumnCount() : 0)
                .headersBefore(beforeSheet != null ? beforeSheet.getHeaders() : Collections.emptyList())
                .headersAfter(afterSheet != null ? afterSheet.getHeaders() : Collections.emptyList())
                .beforeRows(beforeRows)
                .afterRows(afterRows)
                .summary("Batch pipeline with " + requests.size() + " operations evaluated.")
                .build();

        return ResponseEntity.ok(preview);
    }

    @PostMapping("/duplicates")
    @Operation(summary = "Remove duplicate rows",
            description = "Removes duplicate rows based on all or selected columns. Supports KEEP_FIRST and KEEP_LAST.")
    public ResponseEntity<CleaningResult> removeDuplicates(@RequestBody TransformationRequest request) {
        request.setOperation(CleaningOperation.REMOVE_DUPLICATES);
        return executeAndStore(request);
    }

    @PostMapping("/missing-values")
    @Operation(summary = "Handle missing values",
            description = "Drop rows or fill missing values using various strategies (FILL_CONSTANT, FILL_NUMERIC_ZERO, FORWARD_FILL, BACKWARD_FILL)")
    public ResponseEntity<CleaningResult> handleMissingValues(@RequestBody TransformationRequest request) {
        request.setOperation(CleaningOperation.HANDLE_MISSING_VALUES);
        return executeAndStore(request);
    }

    @PostMapping("/strings")
    @Operation(summary = "Clean string values",
            description = "Apply TRIM, NORMALIZE_WHITESPACE, LOWERCASE, or UPPERCASE to a string column")
    public ResponseEntity<CleaningResult> cleanStrings(@RequestBody TransformationRequest request) {
        if (request.getOperation() == null) {
            throw new CleaningException(
                    "operation is required for string cleaning (TRIM, NORMALIZE_WHITESPACE, LOWERCASE, UPPERCASE)");
        }
        return executeAndStore(request);
    }

    @PostMapping("/replace")
    @Operation(summary = "Replace specific values in a column")
    public ResponseEntity<CleaningResult> replaceValues(@RequestBody TransformationRequest request) {
        request.setOperation(CleaningOperation.REPLACE_VALUE);
        return executeAndStore(request);
    }

    @PostMapping("/rename-column")
    @Operation(summary = "Rename a column")
    public ResponseEntity<CleaningResult> renameColumn(@RequestBody TransformationRequest request) {
        request.setOperation(CleaningOperation.RENAME_COLUMN);
        return executeAndStore(request);
    }

    @PostMapping("/remove-column")
    @Operation(summary = "Remove a column from the sheet")
    public ResponseEntity<CleaningResult> removeColumn(@RequestBody TransformationRequest request) {
        request.setOperation(CleaningOperation.REMOVE_COLUMN);
        return executeAndStore(request);
    }

    @PostMapping("/convert-type")
    @Operation(summary = "Convert column values to a target type",
            description = "Convert values to STRING, INTEGER, DECIMAL, BOOLEAN, DATE, or DATETIME")
    public ResponseEntity<CleaningResult> convertType(@RequestBody TransformationRequest request) {
        request.setOperation(CleaningOperation.CONVERT_TYPE);
        return executeAndStore(request);
    }

    @PostMapping("/transform")
    @Operation(summary = "Execute multiple cleaning operations sequentially",
            description = "Each operation's output becomes the next operation's input. Original data is not modified.")
    public ResponseEntity<List<CleaningResult>> transform(@RequestBody List<TransformationRequest> requests) {
        ExcelWorkbookData workbook = getWorkbookOrThrow();
        List<CleaningResult> results = cleaningService.executePipeline(workbook, requests);

        if (!results.isEmpty()) {
            workbookSessionHolder.store(
                    results.get(results.size() - 1).getCleanedWorkbook(),
                    "Batch Pipeline (" + requests.size() + " steps)"
            );
        }

        return ResponseEntity.ok(results);
    }

    private ResponseEntity<CleaningResult> executeAndStore(TransformationRequest request) {
        ExcelWorkbookData workbook = getWorkbookOrThrow();
        CleaningResult result = cleaningService.execute(workbook, request);
        String actionDesc = formatActionDescription(request);
        workbookSessionHolder.store(result.getCleanedWorkbook(), actionDesc);
        return ResponseEntity.ok(result);
    }

    private String formatActionDescription(TransformationRequest request) {
        if (request.getOperation() == null) return "Clean Data";
        return switch (request.getOperation()) {
            case REMOVE_DUPLICATES -> "Remove Duplicates";
            case HANDLE_MISSING_VALUES -> "Handle Missing (" + (request.getColumnName() != null ? request.getColumnName() : "Sheet") + ")";
            case TRIM -> "Trim (" + request.getColumnName() + ")";
            case LOWERCASE -> "Lowercase (" + request.getColumnName() + ")";
            case UPPERCASE -> "Uppercase (" + request.getColumnName() + ")";
            case RENAME_COLUMN -> "Rename " + request.getColumnName() + " -> " + request.getNewValue();
            case REMOVE_COLUMN -> "Remove Column (" + request.getColumnName() + ")";
            case CONVERT_TYPE -> "Convert " + request.getColumnName() + " to " + request.getTargetType();
            case REPLACE_VALUE -> "Replace in " + request.getColumnName();
            default -> request.getOperation().name();
        };
    }

    private ExcelWorkbookData getWorkbookOrThrow() {
        ExcelWorkbookData workbook = workbookSessionHolder.get();
        if (workbook == null) {
            throw new CleaningException(
                    "No workbook loaded. Upload an Excel file first via POST /api/v1/files/excel");
        }
        return workbook;
    }
}
