package com.aditya.dataautomation.job.controller;

import com.aditya.dataautomation.job.dto.AsyncJobDto;
import com.aditya.dataautomation.job.model.JobType;
import com.aditya.dataautomation.job.service.AsyncJobProcessor;
import com.aditya.dataautomation.job.service.AsyncJobService;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Async Job Processing", description = "Non-blocking background worker queue for heavy file imports & batch transformations")
public class AsyncJobController {

    private final AsyncJobService asyncJobService;
    private final AsyncJobProcessor asyncJobProcessor;
    private final com.aditya.dataautomation.idempotency.IdempotencyService idempotencyService;

    @PostMapping(value = "/excel-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit Excel file for non-blocking background parsing")
    public ResponseEntity<?> submitExcelFileAsync(
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required to submit async jobs");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided for asynchronous upload");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Workbook";
        byte[] bytes = file.getBytes();
        long fileSize = file.getSize();
        String contentType = file.getContentType();

        Object job = idempotencyService.execute(principal.getId(), "job-excel-async", idempotencyKey, () -> {
            AsyncJobDto created = asyncJobService.createJob(principal.getId(), JobType.FILE_IMPORT, "Async Import: " + fileName);
            try {
                asyncJobProcessor.processExcelUploadAsync(created.getJobId(), principal.getId(), bytes, fileName, contentType, fileSize);
            } catch (org.springframework.core.task.TaskRejectedException busy) {
                asyncJobService.failJob(created.getJobId(), "Server is busy. Please try again in a minute.");
                throw busy;
            }
            return created;
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Check status, progress percentage and result of a background job")
    public ResponseEntity<AsyncJobDto> getJobStatus(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required to view job status");
        }
        AsyncJobDto job = asyncJobService.getJob(jobId, principal.getId());
        return ResponseEntity.ok(job);
    }

    @GetMapping
    @Operation(summary = "List recent background jobs in the processing queue")
    public ResponseEntity<List<AsyncJobDto>> listJobs(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required to view jobs");
        }
        return ResponseEntity.ok(asyncJobService.listRecentJobs(principal.getId()));
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a pending or running background job")
    public ResponseEntity<Map<String, Object>> cancelJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Authentication required to cancel job");
        }
        boolean cancelled = asyncJobService.cancelJob(jobId, principal.getId());
        if (cancelled) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Job cancelled successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Job cannot be cancelled (already completed or not found)"));
        }
    }
}
