package com.aditya.dataautomation.excel.controller;

import com.aditya.dataautomation.excel.dto.ExcelFileResponse;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.excel.service.ExcelFileService;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Excel Files", description = "Upload and inspect Excel (.xlsx) files")
public class ExcelFileController {

    private final ExcelFileService excelFileService;
    private final WorkbookSessionHolder workbookSessionHolder;
    private final com.aditya.dataautomation.idempotency.IdempotencyService idempotencyService;

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload and analyze an Excel file",
            description = "Accepts an .xlsx file and returns file metadata, sheet information, "
                    + "column headers, and a limited preview of data rows. "
                    + "The workbook is registered in the user's workspace."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File processed successfully",
                    content = @Content(schema = @Schema(implementation = ExcelFileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or missing file"),
            @ApiResponse(responseCode = "413", description = "File exceeds size limit")
    })
    public ResponseEntity<?> uploadExcelFile(
            @Parameter(description = "The .xlsx file to upload", required = true)
            @RequestPart("file") MultipartFile file,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long userId = principal != null ? principal.getId() : null;
        Object response = idempotencyService.execute(userId, "excel-upload", idempotencyKey, () -> {
            // parse ONCE (previously the file was validated + parsed twice)
            ExcelWorkbookData workbookData = excelFileService.parseWorkbook(file);
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "Workbook";

            String fileId = workbookSessionHolder.registerNewWorkbook(workbookData, fileName, file.getSize(), "Upload: " + fileName);

            int totalRows = 0;
            java.util.List<com.aditya.dataautomation.excel.dto.ExcelSheetResponse> sheets = new java.util.ArrayList<>();
            if (workbookData.getSheets() != null) {
                for (com.aditya.dataautomation.excel.model.ExcelSheetData sheet : workbookData.getSheets()) {
                    totalRows += sheet.getRowCount();
                    sheets.add(com.aditya.dataautomation.excel.dto.ExcelSheetResponse.builder()
                            .sheetName(sheet.getSheetName())
                            .rowCount(sheet.getRowCount())
                            .columnCount(sheet.getColumnCount())
                            .headers(sheet.getHeaders() != null ? sheet.getHeaders() : java.util.Collections.emptyList())
                            .previewRows(sheet.getRows() != null ? sheet.getRows().stream().limit(10).toList() : java.util.Collections.emptyList())
                            .build());
                }
            }

            return ExcelFileResponse.builder()
                    .fileId(fileId)
                    .fileName(fileName)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType() != null ? file.getContentType()
                            : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .sheetCount(sheets.size())
                    .totalRows(totalRows)
                    .sheets(sheets)
                    .build();
        });

        return ResponseEntity.ok(response);
    }
}
