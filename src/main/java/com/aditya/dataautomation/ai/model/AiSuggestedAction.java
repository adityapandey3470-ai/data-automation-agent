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
public class AiSuggestedAction {

    private String label;
    private String command;
    private String explanation;
    private boolean requiresConfirmation;
}
