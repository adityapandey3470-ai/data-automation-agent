package com.aditya.dataautomation.service.impl;

import com.aditya.dataautomation.dto.excel.ExcelFileResponse;
import com.aditya.dataautomation.dto.excel.ExcelSheetResponse;
import com.aditya.dataautomation.exception.InvalidExcelFileException;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.ExcelFileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExcelFileServiceImpl implements ExcelFileService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String XLSX_EXTENSION = ".xlsx";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long maxFileSize;
    private final int previewRowLimit;

    public ExcelFileServiceImpl(
            @Value("${app.file.max-size}") long maxFileSize,
            @Value("${app.excel.preview-rows}") int previewRowLimit) {
        this.maxFileSize = maxFileSize;
        this.previewRowLimit = previewRowLimit;
    }

    @Override
    public ExcelFileResponse processExcelFile(MultipartFile file) {
        validateFile(file);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            List<ExcelSheetResponse> sheetResponses = new ArrayList<>();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sheetResponses.add(processSheet(sheet));
            }

            return ExcelFileResponse.builder()
                    .fileName(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(XLSX_CONTENT_TYPE)
                    .sheetCount(workbook.getNumberOfSheets())
                    .sheets(sheetResponses)
                    .build();

        } catch (InvalidExcelFileException e) {

            throw e;
        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage());
            throw new InvalidExcelFileException(
                    "Failed to read the uploaded file. Ensure it is a valid .xlsx workbook.", e);
        } catch (Exception e) {
            log.error("Unexpected error processing Excel file: {}", e.getMessage());
            throw new InvalidExcelFileException(
                    "The uploaded file could not be processed as a valid Excel workbook.", e);
        }
    }

    @Override
    public ExcelWorkbookData parseWorkbook(MultipartFile file) {
        validateFile(file);

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            List<ExcelSheetData> sheetDataList = new ArrayList<>();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sheetDataList.add(parseSheetData(sheet));
            }

            return ExcelWorkbookData.builder()
                    .fileName(file.getOriginalFilename())
                    .sheets(sheetDataList)
                    .build();

        } catch (InvalidExcelFileException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to read Excel file: {}", e.getMessage());
            throw new InvalidExcelFileException(
                    "Failed to read the uploaded file. Ensure it is a valid .xlsx workbook.", e);
        } catch (Exception e) {
            log.error("Unexpected error processing Excel file: {}", e.getMessage());
            throw new InvalidExcelFileException(
                    "The uploaded file could not be processed as a valid Excel workbook.", e);
        }
    }

    /**
     * Parses a POI sheet into the internal model, reading ALL data rows.
     */
    private ExcelSheetData parseSheetData(Sheet sheet) {
        List<String> headers = detectHeaders(sheet);
        List<Map<String, Object>> allRows = readAllDataRows(sheet, headers);

        return ExcelSheetData.builder()
                .sheetName(sheet.getSheetName())
                .headers(headers)
                .rows(allRows)
                .build();
    }

    /**
     * Reads all non-blank data rows (no preview limit).
     */
    private List<Map<String, Object>> readAllDataRows(Sheet sheet, List<String> headers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (headers.isEmpty()) {
            return rows;
        }

        Row headerRow = findFirstNonEmptyRow(sheet);
        if (headerRow == null) {
            return rows;
        }

        int headerRowIndex = headerRow.getRowNum();

        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowBlank(row)) {
                continue;
            }

            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                rowMap.put(headers.get(j), getCellValue(cell));
            }
            rows.add(rowMap);
        }

        return rows;
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidExcelFileException("No file provided or file is empty");
        }

        if (file.getSize() > maxFileSize) {
            throw new InvalidExcelFileException(
                    "File size exceeds the maximum allowed limit of " + (maxFileSize / (1024 * 1024)) + " MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(XLSX_EXTENSION)) {
            throw new InvalidExcelFileException(
                    "Only .xlsx files are supported. Received: " + originalFilename);
        }

        String contentType = file.getContentType();

        if (contentType != null
                && !contentType.equals(XLSX_CONTENT_TYPE)
                && !contentType.equals("application/octet-stream")) {
            throw new InvalidExcelFileException(
                    "Unsupported content type: " + contentType + ". Expected an .xlsx file.");
        }
    }


    private ExcelSheetResponse processSheet(Sheet sheet) {
        List<String> headers = detectHeaders(sheet);
        int dataRowCount = calculateDataRowCount(sheet);
        int columnCount = headers.isEmpty() ? 0 : headers.size();
        List<Map<String, Object>> previewRows = readPreviewRows(sheet, headers);

        return ExcelSheetResponse.builder()
                .sheetName(sheet.getSheetName())
                .rowCount(dataRowCount)
                .columnCount(columnCount)
                .headers(headers)
                .previewRows(previewRows)
                .build();
    }

    private List<String> detectHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row headerRow = findFirstNonEmptyRow(sheet);
        if (headerRow == null) {
            return headers;
        }

        int lastCellNum = headerRow.getLastCellNum();
        for (int i = 0; i < lastCellNum; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String headerValue = getCellValueAsString(cell);

            headers.add(headerValue.isBlank() ? "Column_" + (i + 1) : headerValue);
        }

        return headers;
    }


    private int calculateDataRowCount(Sheet sheet) {
        Row headerRow = findFirstNonEmptyRow(sheet);
        if (headerRow == null) {
            return 0;
        }

        int headerRowIndex = headerRow.getRowNum();
        int count = 0;

        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isRowBlank(row)) {
                count++;
            }
        }
        return count;
    }


    private List<Map<String, Object>> readPreviewRows(Sheet sheet, List<String> headers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (headers.isEmpty()) {
            return rows;
        }

        Row headerRow = findFirstNonEmptyRow(sheet);
        if (headerRow == null) {
            return rows;
        }

        int headerRowIndex = headerRow.getRowNum();
        int rowsCollected = 0;

        for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum() && rowsCollected < previewRowLimit; i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowBlank(row)) {
                continue;
            }

            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                rowMap.put(headers.get(j), getCellValue(cell));
            }
            rows.add(rowMap);
            rowsCollected++;
        }

        return rows;
    }


    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                    yield dateTime != null ? dateTime.format(DATE_FORMATTER) : null;
                }
                double numericValue = cell.getNumericCellValue();

                if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                    yield (long) numericValue;
                }
                yield numericValue;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> getFormulaResultValue(cell);
            case BLANK -> null;
            default -> null;
        };
    }

    private String getCellValueAsString(Cell cell) {
        Object value = getCellValue(cell);
        return value != null ? value.toString() : "";
    }

    private Object getFormulaResultValue(Cell cell) {
        try {
            CellType cachedType = cell.getCachedFormulaResultType();
            return switch (cachedType) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        LocalDateTime dateTime = cell.getLocalDateTimeCellValue();
                        yield dateTime != null ? dateTime.format(DATE_FORMATTER) : null;
                    }
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == Math.floor(numericValue) && !Double.isInfinite(numericValue)) {
                        yield (long) numericValue;
                    }
                    yield numericValue;
                }
                case BOOLEAN -> cell.getBooleanCellValue();
                default -> null;
            };
        } catch (Exception e) {
            log.debug("Could not read cached formula result: {}", e.getMessage());
            return null;
        }
    }

    private Row findFirstNonEmptyRow(Sheet sheet) {
        for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null && !isRowBlank(row)) {
                return row;
            }
        }
        return null;
    }

    private boolean isRowBlank(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            if (cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
