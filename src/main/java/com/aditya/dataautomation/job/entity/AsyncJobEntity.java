package com.aditya.dataautomation.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "async_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJobEntity {

    @Id
    @Column(name = "job_id", length = 40)
    private String jobId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "job_type", length = 30, nullable = false)
    private String jobType;

    @Column(name = "title", length = 300)
    private String title;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "status_message", length = 1000)
    private String statusMessage;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
