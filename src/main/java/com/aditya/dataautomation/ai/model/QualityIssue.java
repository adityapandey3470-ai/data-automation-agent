package com.aditya.dataautomation.ai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QualityIssue {

    private String id;
    private String dimension;       // COMPLETENESS, UNIQUENESS, CONSISTENCY, VALIDITY
    private String severity;        // HIGH, MEDIUM, LOW
    private String column;          // null if sheet-level
    private String title;
    private String description;
    private int issueCount;
    private double impactPercentage;
    private String recommendation;
    private String operation;       // e.g., REMOVE_DUPLICATES, HANDLE_MISSING_VALUES, TRIM
}
