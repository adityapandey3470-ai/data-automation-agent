package com.aditya.dataautomation.cleaning.service.impl;

import com.aditya.dataautomation.cleaning.exception.CleaningException;
import com.aditya.dataautomation.cleaning.model.*;
import com.aditya.dataautomation.cleaning.service.DataCleaningService;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataCleaningServiceImpl implements DataCleaningService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public CleaningResult execute(ExcelWorkbookData workbook, TransformationRequest request) {
        validateWorkbook(workbook);
        if (request == null || request.getOperation() == null) {
            throw new CleaningException("Transformation request and operation must not be null");
        }

        return switch (request.getOperation()) {
            case REMOVE_DUPLICATES -> removeDuplicates(workbook, request);
            case HANDLE_MISSING_VALUES -> handleMissingValues(workbook, request);
            case TRIM -> stringClean(workbook, request, CleaningOperation.TRIM);
            case NORMALIZE_WHITESPACE -> stringClean(workbook, request, CleaningOperation.NORMALIZE_WHITESPACE);
            case LOWERCASE -> stringClean(workbook, request, CleaningOperation.LOWERCASE);
            case UPPERCASE -> stringClean(workbook, request, CleaningOperation.UPPERCASE);
            case REPLACE_VALUE -> replaceValue(workbook, request);
            case RENAME_COLUMN -> renameColumn(workbook, request);
            case REMOVE_COLUMN -> removeColumn(workbook, request);
            case REORDER_COLUMNS -> reorderColumns(workbook, request);
            case CONVERT_TYPE -> convertType(workbook, request);
        };
    }

    @Override
    public List<CleaningResult> executePipeline(ExcelWorkbookData workbook, List<TransformationRequest> requests) {
        validateWorkbook(workbook);
        if (requests == null || requests.isEmpty()) {
            throw new CleaningException("Pipeline must contain at least one transformation request");
        }

        List<CleaningResult> results = new ArrayList<>();
        ExcelWorkbookData current = workbook;

        for (TransformationRequest request : requests) {
            CleaningResult result = execute(current, request);
            results.add(result);
            current = result.getCleanedWorkbook();
        }

        return results;
    }


    private CleaningResult removeDuplicates(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        DuplicateStrategy strategy = request.getDuplicateStrategy() != null
                ? request.getDuplicateStrategy() : DuplicateStrategy.KEEP_FIRST;

        List<String> keyColumns;
        if (request.getColumns() != null && !request.getColumns().isEmpty()) {
            keyColumns = resolveColumns(sheet, request.getColumns());
        } else {
            keyColumns = sheet.getHeaders();
        }

        List<Map<String, Object>> original = sheet.getRows();
        List<Map<String, Object>> cleaned;

        if (strategy == DuplicateStrategy.KEEP_FIRST) {
            cleaned = deduplicateKeepFirst(original, keyColumns);
        } else {
            cleaned = deduplicateKeepLast(original, keyColumns);
        }

        int removed = original.size() - cleaned.size();
        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, cleaned, sheet.getHeaders());

        return CleaningResult.builder()
                .operation("REMOVE_DUPLICATES")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(original.size())
                .rowsAfter(cleaned.size())
                .rowsRemoved(removed)
                .message("Removed " + removed + " duplicate row(s) using strategy " + strategy)
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private List<Map<String, Object>> deduplicateKeepFirst(List<Map<String, Object>> rows, List<String> keyColumns) {
        Set<String> seen = new LinkedHashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = fingerprint(row, keyColumns);
            if (seen.add(key)) {
                result.add(copyRow(row));
            }
        }
        return result;
    }

    private List<Map<String, Object>> deduplicateKeepLast(List<Map<String, Object>> rows, List<String> keyColumns) {
        Map<String, Map<String, Object>> lastSeen = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = fingerprint(row, keyColumns);
            lastSeen.put(key, row);
        }
        return lastSeen.values().stream().map(this::copyRow).collect(Collectors.toList());
    }



    private CleaningResult handleMissingValues(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        String column = resolveColumn(sheet, request.getColumnName());
        MissingValueStrategy strategy = request.getMissingValueStrategy();
        if (strategy == null) {
            throw new CleaningException("missingValueStrategy is required for HANDLE_MISSING_VALUES");
        }

        return switch (strategy) {
            case DROP_ROW -> dropRowsWithMissing(workbook, sheet, column);
            case FILL_CONSTANT -> fillConstant(workbook, sheet, column, request.getValue());
            case FILL_NUMERIC_ZERO -> fillNumericZero(workbook, sheet, column);
            case FORWARD_FILL -> directionalFill(workbook, sheet, column, true);
            case BACKWARD_FILL -> directionalFill(workbook, sheet, column, false);
        };
    }

    private CleaningResult dropRowsWithMissing(ExcelWorkbookData workbook, ExcelSheetData sheet, String column) {
        List<Map<String, Object>> original = sheet.getRows();
        List<Map<String, Object>> cleaned = original.stream()
                .filter(row -> !isNullOrEmpty(row.get(column)))
                .map(this::copyRow)
                .collect(Collectors.toList());

        int removed = original.size() - cleaned.size();
        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, cleaned, sheet.getHeaders());

        return CleaningResult.builder()
                .operation("HANDLE_MISSING_VALUES")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(original.size())
                .rowsAfter(cleaned.size())
                .rowsRemoved(removed)
                .message("Dropped " + removed + " row(s) with missing values in column '" + column + "'")
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private CleaningResult fillConstant(ExcelWorkbookData workbook, ExcelSheetData sheet,
                                        String column, Object fillValue) {
        if (fillValue == null) {
            throw new CleaningException("Fill value must not be null for FILL_CONSTANT strategy");
        }

        List<Map<String, Object>> rows = deepCopyRows(sheet.getRows());
        int changed = 0;
        for (Map<String, Object> row : rows) {
            if (isNullOrEmpty(row.get(column))) {
                row.put(column, fillValue);
                changed++;
            }
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, rows, sheet.getHeaders());
        return CleaningResult.builder()
                .operation("HANDLE_MISSING_VALUES")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(rows.size())
                .rowsAfter(rows.size())
                .cellsChanged(changed)
                .message("Filled " + changed + " missing value(s) in '" + column + "' with '" + fillValue + "'")
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private CleaningResult fillNumericZero(ExcelWorkbookData workbook, ExcelSheetData sheet, String column) {
        List<Map<String, Object>> rows = deepCopyRows(sheet.getRows());
        int changed = 0;
        for (Map<String, Object> row : rows) {
            if (isNullOrEmpty(row.get(column))) {
                row.put(column, 0L);
                changed++;
            }
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, rows, sheet.getHeaders());
        return CleaningResult.builder()
                .operation("HANDLE_MISSING_VALUES")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(rows.size())
                .rowsAfter(rows.size())
                .cellsChanged(changed)
                .message("Filled " + changed + " missing value(s) in '" + column + "' with 0")
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private CleaningResult directionalFill(ExcelWorkbookData workbook, ExcelSheetData sheet,
                                            String column, boolean forward) {
        List<Map<String, Object>> rows = deepCopyRows(sheet.getRows());
        int changed = 0;

        if (forward) {
            Object lastValue = null;
            for (Map<String, Object> row : rows) {
                Object val = row.get(column);
                if (!isNullOrEmpty(val)) {
                    lastValue = val;
                } else if (lastValue != null) {
                    row.put(column, lastValue);
                    changed++;
                }
            }
        } else {
            Object nextValue = null;
            for (int i = rows.size() - 1; i >= 0; i--) {
                Object val = rows.get(i).get(column);
                if (!isNullOrEmpty(val)) {
                    nextValue = val;
                } else if (nextValue != null) {
                    rows.get(i).put(column, nextValue);
                    changed++;
                }
            }
        }

        String dir = forward ? "forward" : "backward";
        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, rows, sheet.getHeaders());
        return CleaningResult.builder()
                .operation("HANDLE_MISSING_VALUES")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(rows.size())
                .rowsAfter(rows.size())
                .cellsChanged(changed)
                .message("Applied " + dir + " fill on '" + column + "', changed " + changed + " cell(s)")
                .cleanedWorkbook(newWorkbook)
                .build();
    }



    private CleaningResult stringClean(ExcelWorkbookData workbook, TransformationRequest request,
                                       CleaningOperation op) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        String column = resolveColumn(sheet, request.getColumnName());

        List<Map<String, Object>> rows = deepCopyRows(sheet.getRows());
        int changed = 0;

        for (Map<String, Object> row : rows) {
            Object val = row.get(column);
            if (val instanceof String s) {
                String transformed = switch (op) {
                    case TRIM -> s.trim();
                    case NORMALIZE_WHITESPACE -> s.trim().replaceAll("\\s+", " ");
                    case LOWERCASE -> s.toLowerCase();
                    case UPPERCASE -> s.toUpperCase();
                    default -> s;
                };
                if (!s.equals(transformed)) {
                    row.put(column, transformed);
                    changed++;
                }
            }
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, rows, sheet.getHeaders());
        return CleaningResult.builder()
                .operation(op.name())
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(rows.size())
                .rowsAfter(rows.size())
                .cellsChanged(changed)
                .message("Applied " + op.name() + " on column '" + column + "', changed " + changed + " cell(s)")
                .cleanedWorkbook(newWorkbook)
                .build();
    }



    private CleaningResult renameColumn(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        String oldName = resolveColumn(sheet, request.getColumnName());
        String newName = request.getNewValue() != null ? request.getNewValue().toString() : null;

        if (newName == null || newName.isBlank()) {
            throw new CleaningException("New column name must not be empty");
        }
        if (sheet.hasColumn(newName) && !newName.equalsIgnoreCase(oldName)) {
            throw new CleaningException("Column '" + newName + "' already exists. Rename would create duplicate.");
        }

        List<String> newHeaders = new ArrayList<>();
        for (String h : sheet.getHeaders()) {
            newHeaders.add(h.equals(oldName) ? newName : h);
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : sheet.getRows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            for (String h : sheet.getHeaders()) {
                String key = h.equals(oldName) ? newName : h;
                newRow.put(key, row.get(h));
            }
            newRows.add(newRow);
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, newRows, newHeaders);
        return CleaningResult.builder()
                .operation("RENAME_COLUMN")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(sheet.getRowCount())
                .rowsAfter(newRows.size())
                .columnCount(newHeaders.size())
                .message("Renamed column '" + oldName + "' to '" + newName + "'")
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private CleaningResult removeColumn(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        String column = resolveColumn(sheet, request.getColumnName());

        List<String> newHeaders = sheet.getHeaders().stream()
                .filter(h -> !h.equals(column))
                .collect(Collectors.toList());

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : sheet.getRows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            for (String h : newHeaders) {
                newRow.put(h, row.get(h));
            }
            newRows.add(newRow);
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, newRows, newHeaders);
        return CleaningResult.builder()
                .operation("REMOVE_COLUMN")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(sheet.getRowCount())
                .rowsAfter(newRows.size())
                .columnCount(newHeaders.size())
                .message("Removed column '" + column + "'")
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private CleaningResult reorderColumns(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());

        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new CleaningException("Column list is required for REORDER_COLUMNS");
        }

        List<String> newOrder = resolveColumns(sheet, request.getColumns());
        if (newOrder.size() != sheet.getHeaders().size()) {
            throw new CleaningException("Reorder must include all columns. Expected " +
                    sheet.getHeaders().size() + " but got " + newOrder.size() +
                    ". Available: " + sheet.getHeaders());
        }

        Set<String> unique = new LinkedHashSet<>(newOrder);
        if (unique.size() != newOrder.size()) {
            throw new CleaningException("Reorder list contains duplicate column names");
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : sheet.getRows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            for (String h : newOrder) {
                newRow.put(h, row.get(h));
            }
            newRows.add(newRow);
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, newRows, newOrder);
        return CleaningResult.builder()
                .operation("REORDER_COLUMNS")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(sheet.getRowCount())
                .rowsAfter(newRows.size())
                .columnCount(newOrder.size())
                .message("Reordered columns to: " + newOrder)
                .cleanedWorkbook(newWorkbook)
                .build();
    }



    private CleaningResult replaceValue(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        String column = resolveColumn(sheet, request.getColumnName());

        Object oldValue = request.getOldValue();
        Object newValue = request.getNewValue();
        if (oldValue == null) {
            throw new CleaningException("oldValue is required for REPLACE_VALUE");
        }

        List<Map<String, Object>> rows = deepCopyRows(sheet.getRows());
        int changed = 0;

        for (Map<String, Object> row : rows) {
            Object cellVal = row.get(column);
            if (valuesMatch(cellVal, oldValue)) {
                row.put(column, newValue);
                changed++;
            }
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, rows, sheet.getHeaders());
        return CleaningResult.builder()
                .operation("REPLACE_VALUE")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(rows.size())
                .rowsAfter(rows.size())
                .cellsChanged(changed)
                .message("Replaced " + changed + " occurrence(s) of '" + oldValue + "' with '" + newValue + "' in '" + column + "'")
                .cleanedWorkbook(newWorkbook)
                .build();
    }



    private CleaningResult convertType(ExcelWorkbookData workbook, TransformationRequest request) {
        ExcelSheetData sheet = resolveSheet(workbook, request.getSheetName());
        String column = resolveColumn(sheet, request.getColumnName());
        TargetDataType targetType = request.getTargetType();
        if (targetType == null) {
            throw new CleaningException("targetType is required for CONVERT_TYPE");
        }

        List<Map<String, Object>> rows = deepCopyRows(sheet.getRows());
        int changed = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            Object val = row.get(column);
            if (val == null) continue;

            try {
                Object converted = convertValue(val, targetType);
                if (!val.equals(converted)) {
                    row.put(column, converted);
                    changed++;
                }
            } catch (Exception e) {
                throw new CleaningException("Cannot convert value '" + val + "' (row " + (i + 1) +
                        ") in column '" + column + "' to " + targetType + ": " + e.getMessage());
            }
        }

        ExcelWorkbookData newWorkbook = replaceSheet(workbook, sheet, rows, sheet.getHeaders());
        return CleaningResult.builder()
                .operation("CONVERT_TYPE")
                .affectedSheet(sheet.getSheetName())
                .rowsBefore(rows.size())
                .rowsAfter(rows.size())
                .cellsChanged(changed)
                .message("Converted " + changed + " value(s) in '" + column + "' to " + targetType)
                .cleanedWorkbook(newWorkbook)
                .build();
    }

    private Object convertValue(Object val, TargetDataType targetType) {
        String str = val.toString().trim();
        return switch (targetType) {
            case STRING -> str;
            case INTEGER -> {
                if (val instanceof Number n) yield n.longValue();
                yield Long.parseLong(str.contains(".") ? str.substring(0, str.indexOf('.')) : str);
            }
            case DECIMAL -> {
                if (val instanceof BigDecimal bd) yield bd;
                if (val instanceof Number n) yield BigDecimal.valueOf(n.doubleValue());
                yield new BigDecimal(str);
            }
            case BOOLEAN -> {
                if (val instanceof Boolean b) yield b;
                if ("true".equalsIgnoreCase(str) || "1".equals(str) || "yes".equalsIgnoreCase(str)) yield true;
                if ("false".equalsIgnoreCase(str) || "0".equals(str) || "no".equalsIgnoreCase(str)) yield false;
                throw new CleaningException("Cannot convert '" + str + "' to BOOLEAN");
            }
            case DATE -> {
                if (val instanceof LocalDate ld) yield ld.format(DATE_FMT);
                // Try parsing as date/datetime string
                try {
                    LocalDate.parse(str, DATE_FMT);
                    yield str;
                } catch (DateTimeParseException e) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(str, DATETIME_FMT);
                        yield dt.toLocalDate().format(DATE_FMT);
                    } catch (DateTimeParseException e2) {
                        throw new CleaningException("Cannot convert '" + str + "' to DATE");
                    }
                }
            }
            case DATETIME -> {
                if (val instanceof LocalDateTime ldt) yield ldt.format(DATETIME_FMT);
                try {
                    LocalDateTime.parse(str, DATETIME_FMT);
                    yield str;
                } catch (DateTimeParseException e) {
                    try {
                        LocalDate ld = LocalDate.parse(str, DATE_FMT);
                        yield ld.atStartOfDay().format(DATETIME_FMT);
                    } catch (DateTimeParseException e2) {
                        throw new CleaningException("Cannot convert '" + str + "' to DATETIME");
                    }
                }
            }
        };
    }


    private void validateWorkbook(ExcelWorkbookData workbook) {
        if (workbook == null) {
            throw new CleaningException("Workbook must not be null");
        }
    }

    private ExcelSheetData resolveSheet(ExcelWorkbookData workbook, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new CleaningException("Sheet name must not be empty");
        }
        ExcelSheetData sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            List<String> available = workbook.getSheets().stream()
                    .map(ExcelSheetData::getSheetName).toList();
            throw new CleaningException("Sheet not found: '" + sheetName + "'. Available: " + available);
        }
        return sheet;
    }

    private String resolveColumn(ExcelSheetData sheet, String columnName) {
        if (columnName == null || columnName.isBlank()) {
            throw new CleaningException("Column name must not be empty");
        }
        String resolved = sheet.resolveColumnName(columnName);
        if (resolved == null) {
            throw new CleaningException("Column not found: '" + columnName +
                    "'. Available: " + sheet.getHeaders());
        }
        return resolved;
    }

    private List<String> resolveColumns(ExcelSheetData sheet, List<String> columns) {
        return columns.stream()
                .map(c -> resolveColumn(sheet, c))
                .collect(Collectors.toList());
    }


    private Map<String, Object> copyRow(Map<String, Object> row) {
        return new LinkedHashMap<>(row);
    }

    private List<Map<String, Object>> deepCopyRows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::copyRow).collect(Collectors.toList());
    }


    private ExcelWorkbookData replaceSheet(ExcelWorkbookData workbook, ExcelSheetData originalSheet,
                                            List<Map<String, Object>> newRows, List<String> newHeaders) {
        ExcelSheetData newSheet = ExcelSheetData.builder()
                .sheetName(originalSheet.getSheetName())
                .headers(new ArrayList<>(newHeaders))
                .rows(newRows)
                .build();

        List<ExcelSheetData> newSheets = workbook.getSheets().stream()
                .map(s -> s.getSheetName().equalsIgnoreCase(originalSheet.getSheetName()) ? newSheet : s)
                .collect(Collectors.toList());

        return ExcelWorkbookData.builder()
                .fileName(workbook.getFileName())
                .sheets(newSheets)
                .build();
    }


    private String fingerprint(Map<String, Object> row, List<String> columns) {
        return columns.stream()
                .map(col -> String.valueOf(row.get(col)))
                .collect(Collectors.joining("|"));
    }

    private boolean isNullOrEmpty(Object val) {
        if (val == null) return true;
        if (val instanceof String s) return s.trim().isEmpty();
        return false;
    }

    private boolean valuesMatch(Object cellVal, Object target) {
        if (cellVal == null && target == null) return true;
        if (cellVal == null || target == null) return false;
        if (cellVal instanceof Number && target instanceof Number) {
            return new BigDecimal(cellVal.toString()).compareTo(new BigDecimal(target.toString())) == 0;
        }
        return cellVal.toString().equalsIgnoreCase(target.toString());
    }
}
