package com.aditya.dataautomation.admin.service;

import com.aditya.dataautomation.admin.dto.AiMetricsDto;
import com.aditya.dataautomation.admin.dto.AiUsageLogDto;
import com.aditya.dataautomation.admin.entity.AiUsageMetric;
import com.aditya.dataautomation.admin.repository.AiUsageMetricRepository;
import com.aditya.dataautomation.ai.service.GeminiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Propagation;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageService {

    private final AiUsageMetricRepository metricRepository;
    private final GeminiClientService geminiClient;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUsage(Long userId, String userEmail, String feature, String modelName,
                            int promptTokens, int completionTokens, long latencyMs,
                            String status, String queryPreview) {
        try {
            int prompt = Math.max(0, promptTokens);
            int completion = Math.max(0, completionTokens);
            int total = prompt + completion;

            String preview = queryPreview != null && queryPreview.length() > 250
                    ? queryPreview.substring(0, 247) + "..."
                    : queryPreview;

            AiUsageMetric metric = AiUsageMetric.builder()
                    .userId(userId)
                    .userEmail(userEmail != null && !userEmail.isBlank() ? userEmail.trim().toLowerCase() : "anonymous")
                    .feature(feature != null ? feature : "AI_ANALYST")
                    .modelName(modelName != null ? modelName : geminiClient.getModelName())
                    .promptTokens(prompt)
                    .completionTokens(completion)
                    .totalTokens(total)
                    .latencyMs(Math.max(0, latencyMs))
                    .status(status != null ? status : "SUCCESS")
                    .queryPreview(preview)
                    .build();

            metricRepository.save(metric);
        } catch (Exception e) {
            log.warn("Failed to persist AI usage metric: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AiMetricsDto getMetrics() {
        long totalCount = metricRepository.count();
        long totalTokens = metricRepository.sumTotalTokens();
        long promptTokens = metricRepository.sumPromptTokens();
        long completionTokens = metricRepository.sumCompletionTokens();
        double avgLatency = metricRepository.avgLatencyMs();

        long analystCount = metricRepository.countByFeature("AI_ANALYST");
        long supportCount = metricRepository.countByFeature("SUPPORT_CHAT");
        long successCount = metricRepository.countByStatus("SUCCESS");
        long fallbackCount = metricRepository.countByStatus("FALLBACK");
        long errorCount = metricRepository.countByStatus("ERROR");

        List<AiUsageMetric> logs = metricRepository.findTop50ByOrderByCreatedAtDesc();
        List<AiUsageLogDto> logDtos = logs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        double costPrompt = (promptTokens / 1_000_000.0) * 0.075;
        double costCompletion = (completionTokens / 1_000_000.0) * 0.30;
        double totalCost = Math.round((costPrompt + costCompletion) * 10000.0) / 10000.0;

        return AiMetricsDto.builder()
                .totalRequests(totalCount)
                .totalTokens(totalTokens)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .analystRequests(analystCount)
                .supportRequests(supportCount)
                .successCount(successCount)
                .fallbackCount(fallbackCount)
                .errorCount(errorCount)
                .averageLatencyMs(Math.round(avgLatency * 10.0) / 10.0)
                .activeModel(geminiClient.getModelName())
                .apiConfigured(geminiClient.isAvailable())
                .estimatedCostUsd(totalCost)
                .freeTierRpmLimit(15)
                .freeTierRpdLimit(1500)
                .recentLogs(logDtos)
                .build();
    }

    private AiUsageLogDto toDto(AiUsageMetric m) {
        return AiUsageLogDto.builder()
                .id(m.getId())
                .userId(m.getUserId())
                .userEmail(m.getUserEmail())
                .feature(m.getFeature())
                .modelName(m.getModelName())
                .promptTokens(m.getPromptTokens())
                .completionTokens(m.getCompletionTokens())
                .totalTokens(m.getTotalTokens())
                .latencyMs(m.getLatencyMs())
                .status(m.getStatus())
                .queryPreview(m.getQueryPreview())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
