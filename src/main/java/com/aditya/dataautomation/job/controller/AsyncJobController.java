package com.aditya.dataautomation.job.controller;

import com.aditya.dataautomation.job.dto.AsyncJobDto;
import com.aditya.dataautomation.job.model.JobType;
import com.aditya.dataautomation.job.service.AsyncJobProcessor;
import com.aditya.dataautomation.job.service.AsyncJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @PostMapping(value = "/excel-async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit Excel file for non-blocking background parsing")
    public ResponseEntity<AsyncJobDto> submitExcelFileAsync(@RequestPart("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided for asynchronous upload");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Workbook";
        byte[] bytes = file.getBytes();
        long fileSize = file.getSize();
        String contentType = file.getContentType();

        AsyncJobDto job = asyncJobService.createJob(JobType.FILE_IMPORT, "Async Import: " + fileName);

        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        if (auth != null && auth.getPrincipal() instanceof com.aditya.dataautomation.auth.security.UserPrincipal p) {
            userId = p.getId();
        }

        asyncJobProcessor.processExcelUploadAsync(job.getJobId(), userId, bytes, fileName, contentType, fileSize);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "Check status, progress percentage and result of a background job")
    public ResponseEntity<AsyncJobDto> getJobStatus(@PathVariable String jobId) {
        AsyncJobDto job = asyncJobService.getJob(jobId);
        return ResponseEntity.ok(job);
    }

    @GetMapping
    @Operation(summary = "List recent background jobs in the processing queue")
    public ResponseEntity<List<AsyncJobDto>> listJobs() {
        return ResponseEntity.ok(asyncJobService.listRecentJobs());
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "Cancel a pending or running background job")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        boolean cancelled = asyncJobService.cancelJob(jobId);
        if (cancelled) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Job cancelled successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Job cannot be cancelled (already completed or not found)"));
        }
    }
}
