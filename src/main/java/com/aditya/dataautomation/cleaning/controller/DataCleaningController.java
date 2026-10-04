package com.aditya.dataautomation.cleaning.controller;

import com.aditya.dataautomation.cleaning.exception.CleaningException;
import com.aditya.dataautomation.cleaning.model.*;
import com.aditya.dataautomation.cleaning.service.DataCleaningService;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cleaning")
@RequiredArgsConstructor
@Tag(name = "Data Cleaning", description = "Clean and transform uploaded Excel data")
public class DataCleaningController {

    private final DataCleaningService cleaningService;
    private final WorkbookSessionHolder workbookSessionHolder;



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
    @Operation(summary = "Replace values in a column",
            description = "Replace all occurrences of oldValue with newValue in the specified column")
    public ResponseEntity<CleaningResult> replaceValue(@RequestBody TransformationRequest request) {
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
            workbookSessionHolder.store(results.get(results.size() - 1).getCleanedWorkbook());
        }

        return ResponseEntity.ok(results);
    }


    private ResponseEntity<CleaningResult> executeAndStore(TransformationRequest request) {
        ExcelWorkbookData workbook = getWorkbookOrThrow();
        CleaningResult result = cleaningService.execute(workbook, request);
        workbookSessionHolder.store(result.getCleanedWorkbook());
        return ResponseEntity.ok(result);
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
