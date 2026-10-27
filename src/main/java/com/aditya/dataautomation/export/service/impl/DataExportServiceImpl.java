package com.aditya.dataautomation.export.service.impl;

import com.aditya.dataautomation.export.exception.ExportException;
import com.aditya.dataautomation.export.service.DataExportService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DataExportServiceImpl implements DataExportService {

    @Override
    public byte[] exportToExcel(ExcelWorkbookData workbook) {
        if (workbook == null || workbook.getSheets() == null || workbook.getSheets().isEmpty()) {
            throw new ExportException("No data to export");
        }

        // SXSSFWorkbook streams rows (100 row memory buffer) avoiding heap explosion and GC pauses
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        wb.setCompressTempFiles(true);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dateStyle = createDateStyle(wb);

            for (ExcelSheetData sheetData : workbook.getSheets()) {
                writeSheet(wb, sheetData, headerStyle, dateStyle);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ExportException("Failed to generate Excel file", e);
        } finally {
            wb.dispose();
        }
    }

    @Override
    public byte[] exportSheetToExcel(ExcelSheetData sheet) {
        if (sheet == null) {
            throw new ExportException("No sheet data to export");
        }

        // SXSSFWorkbook streams rows (100 row memory buffer)
        SXSSFWorkbook wb = new SXSSFWorkbook(100);
        wb.setCompressTempFiles(true);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(wb);
            CellStyle dateStyle = createDateStyle(wb);
            writeSheet(wb, sheet, headerStyle, dateStyle);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ExportException("Failed to generate Excel file", e);
        } finally {
            wb.dispose();
        }
    }

    @Override
    public byte[] exportSheetToCsv(ExcelSheetData sheet) {
        if (sheet == null) {
            throw new ExportException("No sheet data to export");
        }

        List<String> headers = sheet.getHeaders();
        List<Map<String, Object>> rows = sheet.getRows();

        int estimatedSize = (rows != null ? rows.size() + 1 : 1) * Math.max(1, headers.size()) * 20;
        StringBuilder sb = new StringBuilder(Math.max(1024, estimatedSize));

        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(headers.get(i)));
        }
        sb.append('\n');

        if (rows != null) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object val = row.get(headers.get(i));
                    sb.append(escapeCsv(val != null ? val.toString() : ""));
                }
                sb.append('\n');
            }
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeSheet(Workbook wb, ExcelSheetData sheetData,
                            CellStyle headerStyle, CellStyle dateStyle) {
        Sheet sheet = wb.createSheet(sheetData.getSheetName());
        List<String> headers = sheetData.getHeaders();
        List<Map<String, Object>> rows = sheetData.getRows();

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }

        if (rows != null) {
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Map<String, Object> rowData = rows.get(r);
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.createCell(c);
                    Object val = rowData.get(headers.get(c));
                    setCellValue(cell, val, dateStyle);
                }
            }
        }

        // Fast column sizing: sample the header and first 100 rows instead of scanning 68,900 rows
        int sampleLimit = rows != null ? Math.min(rows.size(), 100) : 0;
        for (int i = 0; i < headers.size(); i++) {
            int maxLen = headers.get(i).length();
            for (int r = 0; r < sampleLimit; r++) {
                Object val = rows.get(r).get(headers.get(i));
                if (val != null) {
                    maxLen = Math.max(maxLen, val.toString().length());
                }
            }
            int width = Math.min(50, Math.max(12, maxLen + 3)) * 256;
            sheet.setColumnWidth(i, width);
        }
    }

    private void setCellValue(Cell cell, Object value, CellStyle dateStyle) {
        if (value == null) {
            cell.setBlank();
            return;
        }

        if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof LocalDate date) {
            cell.setCellValue(date);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime dateTime) {
            cell.setCellValue(dateTime);
            cell.setCellStyle(dateStyle);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        CreationHelper helper = wb.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";

        // Mitigate CSV Formula Injection (CWE-1236):
        // If a cell begins with '=', '+', '-', '@', '\t', or '\r', escape it with a single quote prefix
        if (!value.isEmpty()) {
            char firstChar = value.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                    || firstChar == '\t' || firstChar == '\r') {
                value = "'" + value;
            }
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
