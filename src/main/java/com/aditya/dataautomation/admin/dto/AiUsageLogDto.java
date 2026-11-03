package com.aditya.dataautomation.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageLogDto {
    private Long id;
    private Long userId;
    private String userEmail;
    private String feature;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private String status;
    private String queryPreview;
    private LocalDateTime createdAt;
}
