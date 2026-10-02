package com.aditya.dataautomation.controller;

import com.aditya.dataautomation.analysis.exception.AnalysisException;
import com.aditya.dataautomation.analysis.model.*;
import com.aditya.dataautomation.analysis.service.DataAnalysisService;
import com.aditya.dataautomation.dto.analysis.*;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Tag(name = "Data Analysis", description = "Analyze uploaded Excel data")
public class DataAnalysisController {

    private final DataAnalysisService analysisService;
    private final WorkbookSessionHolder workbookSessionHolder;

    // ---- Column Statistics ----

    @PostMapping("/statistics")
    @Operation(summary = "Get column statistics",
            description = "Returns count, nulls, distinct values, and type-specific stats for a column")
    public ResponseEntity<AnalysisResult> getStatistics(@Valid @RequestBody StatisticsRequest request) {
        ExcelSheetData sheet = resolveSheet(request.getSheetName());
        ColumnStatistics stats = analysisService.getColumnStatistics(sheet, request.getColumn());

        return ResponseEntity.ok(AnalysisResult.builder()
                .operation("STATISTICS")
                .sheetName(sheet.getSheetName())
                .totalRows(sheet.getRowCount())
                .statistics(stats)
                .build());
    }

    // ---- Filtering ----

    @PostMapping("/filter")
    @Operation(summary = "Filter rows",
            description = "Returns rows matching all specified filter conditions")
    public ResponseEntity<AnalysisResult> filterRows(@Valid @RequestBody FilterRequest request) {
        ExcelSheetData sheet = resolveSheet(request.getSheetName());

        List<FilterCondition> conditions = request.getConditions().stream()
                .map(dto -> FilterCondition.builder()
                        .column(dto.getColumn())
                        .operator(parseFilterOperator(dto.getOperator()))
                        .value(dto.getValue())
                        .build())
                .toList();

        List<Map<String, Object>> filtered = analysisService.filter(sheet, conditions);

        return ResponseEntity.ok(AnalysisResult.builder()
                .operation("FILTER")
                .sheetName(sheet.getSheetName())
                .totalRows(sheet.getRowCount())
                .resultRows(filtered.size())
                .rows(filtered)
                .build());
    }

    // ---- Sorting ----

    @PostMapping("/sort")
    @Operation(summary = "Sort rows",
            description = "Sorts rows by one or more columns in ASC or DESC order")
    public ResponseEntity<AnalysisResult> sortRows(@Valid @RequestBody SortRequest request) {
        ExcelSheetData sheet = resolveSheet(request.getSheetName());

        List<SortCondition> conditions = request.getSortBy().stream()
                .map(dto -> SortCondition.builder()
                        .column(dto.getColumn())
                        .direction(parseSortDirection(dto.getDirection()))
                        .build())
                .toList();

        List<Map<String, Object>> sorted = analysisService.sort(sheet, conditions);

        return ResponseEntity.ok(AnalysisResult.builder()
                .operation("SORT")
                .sheetName(sheet.getSheetName())
                .totalRows(sheet.getRowCount())
                .resultRows(sorted.size())
                .rows(sorted)
                .build());
    }

    // ---- Group By ----

    @PostMapping("/group")
    @Operation(summary = "Group rows and aggregate",
            description = "Groups rows by a column and applies an aggregation function")
    public ResponseEntity<AnalysisResult> groupBy(@Valid @RequestBody GroupRequest request) {
        ExcelSheetData sheet = resolveSheet(request.getSheetName());
        AggregationFunction aggregation = parseAggregation(request.getAggregation());

        GroupResult result = analysisService.groupBy(
                sheet,
                request.getGroupByColumn(),
                request.getAggregationColumn(),
                aggregation);

        return ResponseEntity.ok(AnalysisResult.builder()
                .operation("GROUP_BY")
                .sheetName(sheet.getSheetName())
                .totalRows(sheet.getRowCount())
                .groupResult(result)
                .build());
    }

    // ---- Duplicates ----

    @PostMapping("/duplicates")
    @Operation(summary = "Detect duplicate rows",
            description = "Finds rows with duplicate values in specified columns (or all columns)")
    public ResponseEntity<AnalysisResult> findDuplicates(@Valid @RequestBody DuplicateRequest request) {
        ExcelSheetData sheet = resolveSheet(request.getSheetName());

        List<Map<String, Object>> duplicates;
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            duplicates = analysisService.findDuplicateRows(sheet);
        } else {
            duplicates = analysisService.findDuplicatesByColumns(sheet, request.getColumns());
        }

        return ResponseEntity.ok(AnalysisResult.builder()
                .operation("DUPLICATE_DETECTION")
                .sheetName(sheet.getSheetName())
                .totalRows(sheet.getRowCount())
                .resultRows(duplicates.size())
                .rows(duplicates)
                .build());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ExcelSheetData resolveSheet(String sheetName) {
        ExcelWorkbookData workbook = workbookSessionHolder.get();
        if (workbook == null) {
            throw new AnalysisException(
                    "No workbook loaded. Upload an Excel file first via POST /api/v1/files/excel");
        }
        ExcelSheetData sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            List<String> available = workbook.getSheets().stream()
                    .map(ExcelSheetData::getSheetName)
                    .toList();
            throw new AnalysisException(
                    "Sheet not found: '" + sheetName + "'. Available sheets: " + available);
        }
        return sheet;
    }

    private FilterOperator parseFilterOperator(String operator) {
        try {
            return FilterOperator.valueOf(operator.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(
                    "Invalid filter operator: '" + operator + "'. Supported: "
                            + java.util.Arrays.toString(FilterOperator.values()));
        }
    }

    private SortDirection parseSortDirection(String direction) {
        try {
            return SortDirection.valueOf(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(
                    "Invalid sort direction: '" + direction + "'. Supported: ASC, DESC");
        }
    }

    private AggregationFunction parseAggregation(String aggregation) {
        try {
            return AggregationFunction.valueOf(aggregation.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AnalysisException(
                    "Invalid aggregation: '" + aggregation + "'. Supported: COUNT, SUM, AVERAGE, MIN, MAX");
        }
    }
}
