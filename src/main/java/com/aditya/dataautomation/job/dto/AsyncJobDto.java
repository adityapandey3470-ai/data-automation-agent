package com.aditya.dataautomation.job.dto;

import com.aditya.dataautomation.job.model.JobStatus;
import com.aditya.dataautomation.job.model.JobType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncJobDto {
    private String jobId;
    private Long userId;
    private JobType jobType;
    private String title;
    private JobStatus status;
    private int progress;
    private String statusMessage;
    private Object resultData;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
