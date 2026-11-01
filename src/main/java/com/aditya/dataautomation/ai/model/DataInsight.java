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
public class DataInsight {

    private String id;
    private String title;
    private String category; // QUALITY, DISTRIBUTION, OUTLIER, ANOMALY, CORRELATION
    private String severity; // INFO, WARNING, SUCCESS
    private String statisticalFact;
    private String interpretation;
    private String affectedColumn;
    private String suggestedAction;
    private String suggestedCommand;
    private Double confidence;
}
