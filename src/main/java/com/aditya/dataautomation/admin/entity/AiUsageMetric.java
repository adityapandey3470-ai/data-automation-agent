package com.aditya.dataautomation.admin.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_metrics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email", length = 190)
    private String userEmail;

    @Column(nullable = false, length = 64)
    private String feature;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private Integer totalTokens;

    @Column(name = "latency_ms", nullable = false)
    private Long latencyMs;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "query_preview", length = 255)
    private String queryPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (promptTokens == null) promptTokens = 0;
        if (completionTokens == null) completionTokens = 0;
        if (totalTokens == null) totalTokens = promptTokens + completionTokens;
        if (latencyMs == null) latencyMs = 0L;
        if (status == null) status = "SUCCESS";
    }
}
