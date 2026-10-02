package com.aditya.dataautomation.cleaning.model;

/**
 * Strategy for handling missing/null values in a column.
 */
public enum MissingValueStrategy {

    /** Remove rows where the target column is null/empty */
    DROP_ROW,

    /** Replace missing values with a supplied constant */
    FILL_CONSTANT,

    /** Replace missing numeric values with 0 */
    FILL_NUMERIC_ZERO,

    /** Use the previous non-empty value in the column */
    FORWARD_FILL,

    /** Use the next non-empty value in the column */
    BACKWARD_FILL
}
