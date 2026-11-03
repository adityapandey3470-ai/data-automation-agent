package com.aditya.dataautomation.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataQualityReport {

    private int overallScore;
    private String grade;
    private String status;
    private int completenessScore;
    private int uniquenessScore;
    private int consistencyScore;
    private int validityScore;
    private int totalIssuesCount;
    private int highSeverityCount;
    private int mediumSeverityCount;
    private int lowSeverityCount;
    private String summaryText;
    private List<QualityIssue> issues;
}
