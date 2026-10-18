package com.aditya.dataautomation.job.service;

import com.aditya.dataautomation.exception.ResourceNotFoundException;
import com.aditya.dataautomation.job.dto.AsyncJobDto;
import com.aditya.dataautomation.job.model.JobStatus;
import com.aditya.dataautomation.job.model.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AsyncJobService {

    private final Map<String, AsyncJobDto> jobStore = new ConcurrentHashMap<>();

    public AsyncJobDto createJob(JobType jobType, String title) {
        String jobId = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        AsyncJobDto job = AsyncJobDto.builder()
                .jobId(jobId)
                .jobType(jobType)
                .title(title)
                .status(JobStatus.PENDING)
                .progress(0)
                .statusMessage("Job queued in worker backlog")
                .createdAt(now)
                .updatedAt(now)
                .build();

        jobStore.put(jobId, job);
        log.info("Created background job: {} [{}] '{}'", jobId, jobType, title);
        pruneOldJobs();
        return job;
    }

    public void startJob(String jobId, String statusMessage) {
        updateJob(jobId, job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setStatusMessage(statusMessage != null ? statusMessage : "Processing started");
            job.setUpdatedAt(LocalDateTime.now());
        });
    }

    public void updateProgress(String jobId, int progress, String statusMessage) {
        updateJob(jobId, job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setProgress(Math.min(100, Math.max(0, progress)));
            if (statusMessage != null) {
                job.setStatusMessage(statusMessage);
            }
            job.setUpdatedAt(LocalDateTime.now());
        });
    }

    public void completeJob(String jobId, Object resultData, String statusMessage) {
        updateJob(jobId, job -> {
            job.setStatus(JobStatus.COMPLETED);
            job.setProgress(100);
            job.setResultData(resultData);
            job.setStatusMessage(statusMessage != null ? statusMessage : "Task completed successfully");
            LocalDateTime now = LocalDateTime.now();
            job.setUpdatedAt(now);
            job.setCompletedAt(now);
        });
        log.info("Completed background job: {}", jobId);
    }

    public void failJob(String jobId, String errorMessage) {
        updateJob(jobId, job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setStatusMessage("Failed: " + errorMessage);
            LocalDateTime now = LocalDateTime.now();
            job.setUpdatedAt(now);
            job.setCompletedAt(now);
        });
        log.warn("Job {} failed: {}", jobId, errorMessage);
    }

    public AsyncJobDto getJob(String jobId) {
        AsyncJobDto job = jobStore.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Async Job", "jobId", jobId);
        }
        return job;
    }

    public List<AsyncJobDto> listRecentJobs() {
        return jobStore.values().stream()
                .sorted(Comparator.comparing(AsyncJobDto::getCreatedAt).reversed())
                .limit(50)
                .toList();
    }

    public boolean cancelJob(String jobId) {
        AsyncJobDto job = jobStore.get(jobId);
        if (job != null && (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING)) {
            job.setStatus(JobStatus.CANCELLED);
            job.setStatusMessage("Cancelled by user request");
            job.setUpdatedAt(LocalDateTime.now());
            job.setCompletedAt(LocalDateTime.now());
            return true;
        }
        return false;
    }

    private void updateJob(String jobId, java.util.function.Consumer<AsyncJobDto> updater) {
        AsyncJobDto job = jobStore.get(jobId);
        if (job != null) {
            synchronized (job) {
                updater.accept(job);
            }
        }
    }

    private void pruneOldJobs() {
        if (jobStore.size() > 500) {
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            jobStore.entrySet().removeIf(entry -> {
                AsyncJobDto j = entry.getValue();
                return (j.getStatus() == JobStatus.COMPLETED || j.getStatus() == JobStatus.FAILED || j.getStatus() == JobStatus.CANCELLED)
                        && j.getCreatedAt().isBefore(cutoff);
            });
        }
    }
}
