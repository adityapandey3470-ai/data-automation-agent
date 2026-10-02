package com.aditya.dataautomation.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ColumnStatistics {

    private final String column;
    private final String dataType;
    private final long count;
    private final long nullCount;
    private final long distinctCount;


    private final Object min;
    private final Object max;
    private final Object sum;
    private final Object average;


    private final List<Map.Entry<String, Long>> topValues;
}
