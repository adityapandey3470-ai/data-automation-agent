package com.aditya.dataautomation.job.service;

import com.aditya.dataautomation.common.util.InMemoryMultipartFile;
import com.aditya.dataautomation.excel.dto.ExcelFileResponse;
import com.aditya.dataautomation.excel.dto.ExcelSheetResponse;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.excel.service.ExcelFileService;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncJobProcessor {

    private final AsyncJobService asyncJobService;
    private final ExcelFileService excelFileService;
    private final WorkbookSessionHolder workbookSessionHolder;

    @Async("jobTaskExecutor")
    public void processExcelUploadAsync(String jobId, Long userId, byte[] bytes, String fileName, String contentType, long fileSize) {
        log.info("Starting background worker for job {}: userId={}, fileName='{}' ({} bytes)", jobId, userId, fileName, fileSize);
        if (!asyncJobService.startJob(jobId, "Worker thread assigned. Reading file stream...")) {
            log.info("Job {} is no longer active (cancelled?) - skipping", jobId);
            return;
        }

        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required for background processing");
            }

            InMemoryMultipartFile file = new InMemoryMultipartFile("file", fileName, contentType, bytes);

            asyncJobService.updateProgress(jobId, 30, "Parsing workbook, worksheets and column types...");
            ExcelWorkbookData workbookData = excelFileService.parseWorkbook(file);

            if (!asyncJobService.updateProgress(jobId, 75, "Registering active workspace session and history...")
                    || asyncJobService.isCancelled(jobId)) {
                log.info("Job {} was cancelled before registration - discarding parsed workbook", jobId);
                return;
            }
            String fileId = workbookSessionHolder.registerNewWorkbook(userId, workbookData, fileName, fileSize, "Async Upload: " + fileName);

            int totalRows = 0;
            List<ExcelSheetResponse> sheetResponses = new ArrayList<>();
            if (workbookData.getSheets() != null) {
                for (ExcelSheetData sheet : workbookData.getSheets()) {
                    int rows = sheet.getRowCount();
                    totalRows += rows;
                    sheetResponses.add(ExcelSheetResponse.builder()
                            .sheetName(sheet.getSheetName())
                            .rowCount(rows)
                            .columnCount(sheet.getColumnCount())
                            .headers(sheet.getHeaders() != null ? sheet.getHeaders() : Collections.emptyList())
                            .previewRows(sheet.getRows() != null ? sheet.getRows().stream().limit(10).toList() : Collections.emptyList())
                            .build());
                }
            }

            ExcelFileResponse result = ExcelFileResponse.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .contentType(contentType != null ? contentType : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .sheetCount(sheetResponses.size())
                    .totalRows(totalRows)
                    .sheets(sheetResponses)
                    .build();

            asyncJobService.completeJob(jobId, result, "Workbook parsed successfully: " + totalRows + " rows across " + result.getSheetCount() + " sheets");
            log.info("Finished background worker for job {}: fileId={}", jobId, fileId);

        } catch (Exception ex) {
            log.error("Background job {} failed: {}", jobId, ex.getMessage(), ex);
            boolean userFacing = ex instanceof com.aditya.dataautomation.exception.InvalidRequestException
                    || ex instanceof IllegalArgumentException;
            asyncJobService.failJob(jobId, userFacing && ex.getMessage() != null
                    ? ex.getMessage() : "Error processing file in background");
        } catch (Error fatal) {
            asyncJobService.failJob(jobId, "Server ran out of resources while processing this file.");
            throw fatal;
        }
    }
}
