package com.aditya.dataautomation.cleaning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Structured request for a single cleaning/transformation operation.
 * Designed so a future AI layer can construct these programmatically.
 * <p>
 * Not every field is used by every operation. Unused fields are null.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationRequest {

    /** The sheet to operate on */
    private String sheetName;

    /** The cleaning operation to perform */
    private CleaningOperation operation;

    /** Target column (for single-column operations) */
    private String columnName;

    /** Target columns (for multi-column operations like REMOVE_DUPLICATES, REORDER) */
    private List<String> columns;

    /** Fill value or replacement value */
    private Object value;

    /** Old value for REPLACE_VALUE */
    private Object oldValue;

    /** New value for REPLACE_VALUE or new name for RENAME_COLUMN */
    private Object newValue;

    /** Target type for CONVERT_TYPE */
    private TargetDataType targetType;

    /** Strategy for HANDLE_MISSING_VALUES */
    private MissingValueStrategy missingValueStrategy;

    /** Strategy for REMOVE_DUPLICATES (default: KEEP_FIRST) */
    private DuplicateStrategy duplicateStrategy;
}
