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
    private String dimension;
    private String severity;
    private String column;
    private String title;
    private String description;
    private int issueCount;
    private double impactPercentage;
    private String recommendation;
    private String operation;
}
