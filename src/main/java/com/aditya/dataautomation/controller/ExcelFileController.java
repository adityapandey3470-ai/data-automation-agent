package com.aditya.dataautomation.controller;

import com.aditya.dataautomation.dto.excel.ExcelFileResponse;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.ExcelFileService;
import com.aditya.dataautomation.service.WorkbookSessionHolder;
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

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Excel Files", description = "Upload and inspect Excel (.xlsx) files")
public class ExcelFileController {

    private final ExcelFileService excelFileService;
    private final WorkbookSessionHolder workbookSessionHolder;

    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload and analyze an Excel file",
            description = "Accepts an .xlsx file and returns file metadata, sheet information, "
                    + "column headers, and a limited preview of data rows. "
                    + "The workbook is also stored in memory for subsequent analysis operations."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File processed successfully",
                    content = @Content(schema = @Schema(implementation = ExcelFileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or missing file"),
            @ApiResponse(responseCode = "413", description = "File exceeds size limit")
    })
    public ResponseEntity<ExcelFileResponse> uploadExcelFile(
            @Parameter(description = "The .xlsx file to upload", required = true)
            @RequestPart("file") MultipartFile file) {

        ExcelFileResponse response = excelFileService.processExcelFile(file);


        ExcelWorkbookData workbookData = excelFileService.parseWorkbook(file);
        workbookSessionHolder.store(workbookData);

        return ResponseEntity.ok(response);
    }
}
