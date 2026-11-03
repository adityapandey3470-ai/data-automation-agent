package com.aditya.dataautomation.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMetricsDto {
    private long totalRequests;
    private long totalTokens;
    private long promptTokens;
    private long completionTokens;
    private long analystRequests;
    private long supportRequests;
    private long successCount;
    private long fallbackCount;
    private long errorCount;
    private double averageLatencyMs;
    private String activeModel;
    private boolean apiConfigured;
    private double estimatedCostUsd;
    private int freeTierRpmLimit;
    private int freeTierRpdLimit;
    private List<AiUsageLogDto> recentLogs;
}
