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
public class AnalysisResult {

    private final String operation;
    private final String sheetName;
    private final int totalRows;
    private final int resultRows;


    private final List<Map<String, Object>> rows;


    private final ColumnStatistics statistics;


    private final GroupResult groupResult;


    private final Object value;
}
