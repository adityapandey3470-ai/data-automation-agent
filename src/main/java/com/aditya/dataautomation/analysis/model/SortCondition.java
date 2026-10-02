package com.aditya.dataautomation.analysis.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
@AllArgsConstructor
public class SortCondition {

    private final String column;
    private final SortDirection direction;
}
