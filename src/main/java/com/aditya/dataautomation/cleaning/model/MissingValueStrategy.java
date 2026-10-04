package com.aditya.dataautomation.cleaning.model;

public enum MissingValueStrategy {


    DROP_ROW,
    FILL_CONSTANT,
    FILL_NUMERIC_ZERO,
    FORWARD_FILL,
    BACKWARD_FILL
}
