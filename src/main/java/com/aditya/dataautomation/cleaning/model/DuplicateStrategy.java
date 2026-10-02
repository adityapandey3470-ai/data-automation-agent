package com.aditya.dataautomation.cleaning.model;

/**
 * Strategy for which duplicate to keep when removing duplicates.
 */
public enum DuplicateStrategy {

    /** Keep the first occurrence, remove subsequent duplicates */
    KEEP_FIRST,

    /** Keep the last occurrence, remove earlier duplicates */
    KEEP_LAST
}
