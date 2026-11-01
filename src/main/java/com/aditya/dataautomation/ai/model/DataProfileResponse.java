package com.aditya.dataautomation.ai.model;

import com.aditya.dataautomation.analysis.model.ColumnStatistics;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataProfileResponse {

    private String sheetName;
    private int totalRows;
    private int totalColumns;
    private long totalCells;
    private long missingCells;
    private double completenessPercentage;
    private int duplicateRowsCount;
    private double duplicatePercentage;
    private List<String> headers;
    private Map<String, ColumnStatistics> columnStatistics;
    private DataQualityReport qualityReport;
    private List<DataInsight> insights;
    private List<String> suggestedQuestions;
}
