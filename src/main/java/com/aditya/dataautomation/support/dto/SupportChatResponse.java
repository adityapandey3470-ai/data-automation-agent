package com.aditya.dataautomation.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupportChatResponse {

    private String reply;

    @Builder.Default
    private List<String> suggestedFollowUps = new ArrayList<>();

    @Builder.Default
    private boolean canEscalateToTicket = false;

    private String engine; // "GEMINI_AI" or "KNOWLEDGE_BASE"
}
