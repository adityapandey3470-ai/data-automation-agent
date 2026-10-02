package com.aditya.dataautomation.cleaning.model;

import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of a cleaning/transformation operation.
 * Contains metadata about what changed and the new cleaned workbook.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CleaningResult {

    /** The operation that was performed */
    private final String operation;

    /** The sheet that was affected */
    private final String affectedSheet;

    /** Row count before the operation */
    private final int rowsBefore;

    /** Row count after the operation */
    private final int rowsAfter;

    /** Number of rows removed (for duplicate/drop operations) */
    private final Integer rowsRemoved;

    /** Number of cells changed (for fill/transform operations) */
    private final Integer cellsChanged;

    /** Number of columns in the result */
    private final Integer columnCount;

    /** Human-readable summary */
    private final String message;

    /** The cleaned workbook — excluded from JSON to avoid huge payloads */
    @JsonIgnore
    private final ExcelWorkbookData cleanedWorkbook;
}
