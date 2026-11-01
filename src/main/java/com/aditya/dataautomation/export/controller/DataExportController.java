package com.aditya.dataautomation.export.controller;

import com.aditya.dataautomation.export.exception.ExportException;
import com.aditya.dataautomation.export.service.DataExportService;
import com.aditya.dataautomation.export.service.PdfReportExportService;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.WorkbookSessionHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@Tag(name = "Data Export", description = "Export processed data as Excel or CSV")
public class DataExportController {

    private final DataExportService exportService;
    private final PdfReportExportService pdfReportExportService;
    private final WorkbookSessionHolder workbookSessionHolder;

    @GetMapping("/excel")
    @Operation(summary = "Export workbook as .xlsx",
            description = "Exports the current processed workbook (all sheets) as an Excel file")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(required = false) String sheetName) {

        ExcelWorkbookData workbook = getWorkbookOrThrow();

        byte[] bytes;
        String filename;

        if (sheetName != null && !sheetName.isBlank()) {
            ExcelSheetData sheet = resolveSheet(workbook, sheetName);
            bytes = exportService.exportSheetToExcel(sheet);
            filename = sanitizeFilename(sheet.getSheetName()) + ".xlsx";
        } else {
            bytes = exportService.exportToExcel(workbook);
            filename = sanitizeFilename(workbook.getFileName() != null
                    ? workbook.getFileName().replaceAll("\\.[^.]+$", "") + "_processed"
                    : "export") + ".xlsx";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/csv")
    @Operation(summary = "Export a sheet as .csv",
            description = "Exports a single sheet as a UTF-8 CSV file")
    public ResponseEntity<byte[]> exportCsv(@RequestParam String sheetName) {

        ExcelWorkbookData workbook = getWorkbookOrThrow();
        ExcelSheetData sheet = resolveSheet(workbook, sheetName);

        byte[] bytes = exportService.exportSheetToCsv(sheet);
        String filename = sanitizeFilename(sheet.getSheetName()) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    @GetMapping("/report/pdf")
    @Operation(summary = "Export Executive PDF Analysis Report",
            description = "Generates and downloads a comprehensive executive PDF report including KPIs, AI data quality scores, issues, and column metrics.")
    public ResponseEntity<byte[]> exportPdfReport(@RequestParam(required = false) String sheetName) {
        ExcelWorkbookData workbook = getWorkbookOrThrow();
        byte[] bytes = pdfReportExportService.generateExecutivePdfReport(workbook, sheetName);
        String filename = sanitizeFilename(workbook.getFileName() != null
                ? workbook.getFileName().replaceAll("\\.[^.]+$", "") + "_Executive_Report"
                : "Executive_Analysis_Report") + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(bytes.length)
                .body(bytes);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ExcelWorkbookData getWorkbookOrThrow() {
        ExcelWorkbookData workbook = workbookSessionHolder.get();
        if (workbook == null) {
            throw new ExportException(
                    "No workbook loaded. Upload an Excel file first via POST /api/v1/files/excel");
        }
        return workbook;
    }

    private ExcelSheetData resolveSheet(ExcelWorkbookData workbook, String sheetName) {
        ExcelSheetData sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new ExportException("Sheet not found: '" + sheetName + "'");
        }
        return sheet;
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "export";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
