package com.aditya.dataautomation.job.service;

import com.aditya.dataautomation.exception.ResourceNotFoundException;
import com.aditya.dataautomation.job.dto.AsyncJobDto;
import com.aditya.dataautomation.job.model.JobStatus;
import com.aditya.dataautomation.job.model.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AsyncJobService {

    private final Map<String, AsyncJobDto> jobStore = new ConcurrentHashMap<>();

    public AsyncJobDto createJob(JobType jobType, String title) {
        return createJob(null, jobType, title);
    }

    public AsyncJobDto createJob(Long userId, JobType jobType, String title) {
        String jobId = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        AsyncJobDto job = AsyncJobDto.builder()
                .jobId(jobId)
                .userId(userId)
                .jobType(jobType)
                .title(title)
                .status(JobStatus.PENDING)
                .progress(0)
                .statusMessage("Job queued in worker backlog")
                .createdAt(now)
                .updatedAt(now)
                .build();

        jobStore.put(jobId, job);
        log.info("Created background job: {} [{}] '{}' for userId={}", jobId, jobType, title, userId);
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
        return getJob(jobId, null);
    }

    public AsyncJobDto getJob(String jobId, Long userId) {
        AsyncJobDto job = jobStore.get(jobId);
        if (job == null) {
            throw new ResourceNotFoundException("Async Job", "jobId", jobId);
        }
        if (userId == null || job.getUserId() == null || !Objects.equals(job.getUserId(), userId)) {
            throw new AccessDeniedException("You do not have permission to view this job");
        }
        return job;
    }

    public List<AsyncJobDto> listRecentJobs() {
        return listRecentJobs(null);
    }

    public List<AsyncJobDto> listRecentJobs(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return jobStore.values().stream()
                .filter(j -> Objects.equals(j.getUserId(), userId))
                .sorted(Comparator.comparing(AsyncJobDto::getCreatedAt).reversed())
                .limit(50)
                .toList();
    }

    public boolean cancelJob(String jobId) {
        return cancelJob(jobId, null);
    }

    public boolean cancelJob(String jobId, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Authentication required to cancel this job");
        }
        AsyncJobDto job = jobStore.get(jobId);
        if (job != null) {
            if (job.getUserId() == null || !Objects.equals(job.getUserId(), userId)) {
                throw new AccessDeniedException("You do not have permission to cancel this job");
            }
            if (job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.RUNNING) {
                job.setStatus(JobStatus.CANCELLED);
                job.setStatusMessage("Cancelled by user request");
                job.setUpdatedAt(LocalDateTime.now());
                job.setCompletedAt(LocalDateTime.now());
                return true;
            }
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
