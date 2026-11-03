package com.aditya.dataautomation.ai.model;

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
public class AiAnalystResponse {

    private String status;
    private String engine;
    private String model;
    private String answer;
    private List<DataInsight> insights;
    private Map<String, Object> verifiedEvidence;
    private List<AiSuggestedAction> suggestedActions;
    private List<String> suggestedFollowUps;
    private Long latencyMs;
    private String subscriptionTier;
    private Integer queriesUsed;
    private Integer queriesLimit;
    private Integer remainingQueries;
    private Integer tokensConsumed;
}
