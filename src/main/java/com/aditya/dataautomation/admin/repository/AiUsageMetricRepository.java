package com.aditya.dataautomation.admin.repository;

import com.aditya.dataautomation.admin.entity.AiUsageMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiUsageMetricRepository extends JpaRepository<AiUsageMetric, Long> {

    long countByFeature(String feature);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM AiUsageMetric m WHERE m.userId = :userId")
    int deleteByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(m.totalTokens), 0) FROM AiUsageMetric m")
    Long sumTotalTokens();

    @Query("SELECT COALESCE(SUM(m.promptTokens), 0) FROM AiUsageMetric m")
    Long sumPromptTokens();

    @Query("SELECT COALESCE(SUM(m.completionTokens), 0) FROM AiUsageMetric m")
    Long sumCompletionTokens();

    @Query("SELECT COALESCE(AVG(m.latencyMs), 0.0) FROM AiUsageMetric m")
    Double avgLatencyMs();

    List<AiUsageMetric> findTop50ByOrderByCreatedAtDesc();
}
