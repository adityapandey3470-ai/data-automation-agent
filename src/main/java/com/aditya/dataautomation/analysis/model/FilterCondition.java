package com.aditya.dataautomation.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FilterCondition {

    private final String column;
    private final FilterOperator operator;
    private final Object value;
}
