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

    private int overallScore;          // 0 to 100
    private String grade;              // A+, A, B, C, D, F
    private String status;             // EXCELLENT, GOOD, FAIR, NEEDS_ATTENTION, CRITICAL
    private int completenessScore;     // 0 to 100
    private int uniquenessScore;       // 0 to 100
    private int consistencyScore;      // 0 to 100
    private int validityScore;         // 0 to 100
    private int totalIssuesCount;
    private int highSeverityCount;
    private int mediumSeverityCount;
    private int lowSeverityCount;
    private String summaryText;
    private List<QualityIssue> issues;
}
