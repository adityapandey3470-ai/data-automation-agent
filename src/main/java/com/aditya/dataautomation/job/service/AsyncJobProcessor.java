package com.aditya.dataautomation.job.service;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.common.util.InMemoryMultipartFile;
import com.aditya.dataautomation.excel.dto.ExcelFileResponse;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.excel.service.ExcelFileService;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncJobProcessor {

    private final AsyncJobService asyncJobService;
    private final ExcelFileService excelFileService;
    private final WorkbookSessionHolder workbookSessionHolder;
    private final UserRepository userRepository;

    @Async("jobTaskExecutor")
    public void processExcelUploadAsync(String jobId, Long userId, byte[] bytes, String fileName, String contentType, long fileSize) {
        log.info("Starting background worker for job {}: userId={}, fileName='{}' ({} bytes)", jobId, userId, fileName, fileSize);
        asyncJobService.startJob(jobId, "Worker thread assigned. Reading file stream...");

        try {
            Long effectiveUserId = userId;
            if (effectiveUserId == null) {
                effectiveUserId = userRepository.findAll().stream().findFirst().map(User::getId).orElse(null);
            }

            InMemoryMultipartFile file = new InMemoryMultipartFile("file", fileName, contentType, bytes);

            asyncJobService.updateProgress(jobId, 15, "Inspecting workbook signature and sheet metadata...");
            ExcelFileResponse parsedResponse = excelFileService.processExcelFile(file);

            asyncJobService.updateProgress(jobId, 50, "Parsing worksheets, column types and cell values...");
            ExcelWorkbookData workbookData = excelFileService.parseWorkbook(file);

            asyncJobService.updateProgress(jobId, 85, "Registering active workspace session and history...");
            String fileId = workbookSessionHolder.registerNewWorkbook(effectiveUserId, workbookData, fileName, fileSize, "Async Upload: " + fileName);

            int totalRows = 0;
            if (parsedResponse.getSheets() != null) {
                for (var s : parsedResponse.getSheets()) {
                    totalRows += s.getRowCount();
                }
            }

            ExcelFileResponse result = ExcelFileResponse.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .contentType(parsedResponse.getContentType())
                    .sheetCount(parsedResponse.getSheetCount())
                    .totalRows(totalRows)
                    .sheets(parsedResponse.getSheets())
                    .build();

            asyncJobService.completeJob(jobId, result, "Workbook parsed successfully: " + totalRows + " rows across " + result.getSheetCount() + " sheets");
            log.info("Finished background worker for job {}: fileId={}", jobId, fileId);

        } catch (Exception ex) {
            log.error("Background job {} failed: {}", jobId, ex.getMessage(), ex);
            asyncJobService.failJob(jobId, ex.getMessage() != null ? ex.getMessage() : "Error processing file in background");
        }
    }
}
