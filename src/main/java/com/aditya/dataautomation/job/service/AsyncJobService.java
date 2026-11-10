package com.aditya.dataautomation.job.service;

import com.aditya.dataautomation.exception.ResourceNotFoundException;
import com.aditya.dataautomation.job.dto.AsyncJobDto;
import com.aditya.dataautomation.job.entity.AsyncJobEntity;
import com.aditya.dataautomation.job.model.JobStatus;
import com.aditya.dataautomation.job.model.JobType;
import com.aditya.dataautomation.job.repository.AsyncJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncJobService {

    private static final int MAX_ACTIVE_JOBS_PER_USER = 3;

    private final AsyncJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AsyncJobDto createJob(JobType jobType, String title) {
        return createJob(null, jobType, title);
    }

    @Transactional
    public AsyncJobDto createJob(Long userId, JobType jobType, String title) {
        if (userId != null && jobRepository.countByUserIdAndStatusIn(userId,
                List.of(JobStatus.PENDING.name(), JobStatus.RUNNING.name())) >= MAX_ACTIVE_JOBS_PER_USER) {
            throw new com.aditya.dataautomation.exception.TooManyAttemptsException(
                    "You already have " + MAX_ACTIVE_JOBS_PER_USER + " background jobs running. Please wait for one to finish.", 15);
        }
        String jobId = "job-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        AsyncJobEntity entity = AsyncJobEntity.builder()
                .jobId(jobId)
                .userId(userId)
                .jobType(jobType != null ? jobType.name() : JobType.DATA_CLEANING.name())
                .title(title)
                .status(JobStatus.PENDING.name())
                .progress(0)
                .statusMessage("Job queued in worker backlog")
                .createdAt(now)
                .updatedAt(now)
                .build();

        entity = jobRepository.save(entity);
        log.info("Created background job: {} [{}] '{}' for userId={}", jobId, jobType, title, userId);
        return toDto(entity);
    }

    /** @return false when the job is already finished/cancelled (worker should stop). */
    public boolean startJob(String jobId, String statusMessage) {
        return jobRepository.markRunning(jobId, statusMessage != null ? statusMessage : "Processing started", LocalDateTime.now()) > 0;
    }

    /** @return false when the job is no longer active (e.g. cancelled) so the worker can abort. */
    public boolean updateProgress(String jobId, int progress, String statusMessage) {
        return jobRepository.updateProgressIfActive(jobId, Math.min(100, Math.max(0, progress)),
                statusMessage != null ? statusMessage : "Processing", LocalDateTime.now()) > 0;
    }

    /** @return false if the job was cancelled/failed meanwhile (the result is then NOT written). */
    public boolean completeJob(String jobId, Object resultData, String statusMessage) {
        String resultJson = null;
        if (resultData != null) {
            try {
                resultJson = objectMapper.writeValueAsString(resultData);
            } catch (Exception e) {
                log.warn("Failed to serialize resultData for job {}: {}", jobId, e.getMessage());
                resultJson = String.valueOf(resultData);
            }
        }
        boolean ok = jobRepository.completeIfActive(jobId,
                statusMessage != null ? statusMessage : "Task completed successfully", resultJson, LocalDateTime.now()) > 0;
        if (ok) {
            log.info("Completed background job: {}", jobId);
        }
        return ok;
    }

    public boolean failJob(String jobId, String errorMessage) {
        String err = errorMessage != null ? errorMessage : "Job failed";
        boolean ok = jobRepository.failIfActive(jobId, err, "Failed: " + err, LocalDateTime.now()) > 0;
        if (ok) {
            log.warn("Job {} failed: {}", jobId, err);
        }
        return ok;
    }

    @Transactional(readOnly = true)
    public boolean isCancelled(String jobId) {
        return jobRepository.existsByJobIdAndStatus(jobId, JobStatus.CANCELLED.name());
    }

    /** Called on startup and hourly: jobs whose worker vanished must not stay PENDING/RUNNING forever. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverStaleJobsOnStartup() {
        failStaleJobs(30);
    }

    public void failStaleJobs(int staleMinutes) {
        LocalDateTime now = LocalDateTime.now();
        int n = jobRepository.failStaleActive(now.minusMinutes(staleMinutes), now);
        if (n > 0) {
            log.warn("Marked {} stale background jobs as FAILED", n);
        }
    }

    @Transactional(readOnly = true)
    public AsyncJobDto getJob(String jobId) {
        return getJob(jobId, null);
    }

    @Transactional(readOnly = true)
    public AsyncJobDto getJob(String jobId, Long userId) {
        AsyncJobEntity entity = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Async Job", "jobId", jobId));

        if (userId != null && !Objects.equals(entity.getUserId(), userId)) {
            throw new AccessDeniedException("You do not have permission to view this job");
        }
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<AsyncJobDto> listRecentJobs() {
        return listRecentJobs(null);
    }

    @Transactional(readOnly = true)
    public List<AsyncJobDto> listRecentJobs(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return jobRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public boolean cancelJob(String jobId) {
        return cancelJob(jobId, null);
    }

    @Transactional
    public boolean cancelJob(String jobId, Long userId) {
        Optional<AsyncJobEntity> opt = jobRepository.findById(jobId);
        if (opt.isEmpty()) {
            return false;
        }
        AsyncJobEntity entity = opt.get();
        if (userId != null && !Objects.equals(entity.getUserId(), userId)) {
            throw new AccessDeniedException("You do not have permission to cancel this job");
        }
        // atomic: only an active job can be cancelled; the worker observes this via updateProgress()/isCancelled()
        return jobRepository.cancelIfActive(jobId, LocalDateTime.now()) > 0;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOldJobs() {
        failStaleJobs(60);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(48);
        int deleted = jobRepository.deleteOldJobs(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} old completed/failed async jobs older than 48 hours", deleted);
        }
    }

    private AsyncJobDto toDto(AsyncJobEntity entity) {
        Object parsedResult = null;
        if (entity.getResultJson() != null && !entity.getResultJson().isBlank()) {
            try {
                parsedResult = objectMapper.readValue(entity.getResultJson(), Object.class);
            } catch (Exception e) {
                parsedResult = entity.getResultJson();
            }
        }

        JobType jt = null;
        if (entity.getJobType() != null) {
            try {
                jt = JobType.valueOf(entity.getJobType());
            } catch (Exception ignored) {}
        }

        JobStatus js = null;
        if (entity.getStatus() != null) {
            try {
                js = JobStatus.valueOf(entity.getStatus());
            } catch (Exception ignored) {}
        }

        return AsyncJobDto.builder()
                .jobId(entity.getJobId())
                .userId(entity.getUserId())
                .jobType(jt)
                .title(entity.getTitle())
                .status(js)
                .progress(entity.getProgress())
                .statusMessage(entity.getStatusMessage())
                .resultData(parsedResult)
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .completedAt(entity.getCompletedAt())
                .build();
    }
}
