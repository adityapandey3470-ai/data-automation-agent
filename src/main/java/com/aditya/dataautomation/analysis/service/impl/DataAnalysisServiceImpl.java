package com.aditya.dataautomation.analysis.service.impl;

import com.aditya.dataautomation.analysis.exception.AnalysisException;
import com.aditya.dataautomation.analysis.model.*;
import com.aditya.dataautomation.analysis.service.DataAnalysisService;
import com.aditya.dataautomation.common.util.NumericParser;
import com.aditya.dataautomation.common.util.RowFingerprintUtil;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataAnalysisServiceImpl implements DataAnalysisService {

    private static final int TOP_VALUES_LIMIT = 10;
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public int countRows(ExcelSheetData sheet) {
        validateSheet(sheet);
        return sheet.getRowCount();
    }

    @Override
    public long countNonEmpty(ExcelSheetData sheet, String column) {
        validateSheetAndColumn(sheet, column);
        String resolved = sheet.resolveColumnName(column);
        return sheet.getRows().stream()
                .filter(row -> row.get(resolved) != null)
                .count();
    }

    @Override
    public long countNulls(ExcelSheetData sheet, String column) {
        validateSheetAndColumn(sheet, column);
        String resolved = sheet.resolveColumnName(column);
        return sheet.getRows().stream()
                .filter(row -> row.get(resolved) == null)
                .count();
    }

    @Override
    public Set<Object> distinctValues(ExcelSheetData sheet, String column) {
        validateSheetAndColumn(sheet, column);
        String resolved = sheet.resolveColumnName(column);
        return sheet.getRows().stream()
                .map(row -> row.get(resolved))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Object sum(ExcelSheetData sheet, String column) {
        List<BigDecimal> values = extractNumericValues(sheet, column);
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public Object average(ExcelSheetData sheet, String column) {
        List<BigDecimal> values = extractNumericValues(sheet, column);
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    @Override
    public Object min(ExcelSheetData sheet, String column) {
        List<BigDecimal> values = extractNumericValues(sheet, column);
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().min(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public Object max(ExcelSheetData sheet, String column) {
        List<BigDecimal> values = extractNumericValues(sheet, column);
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public List<Map<String, Object>> filter(ExcelSheetData sheet, List<FilterCondition> conditions) {
        validateSheet(sheet);
        if (conditions == null || conditions.isEmpty()) {
            return new ArrayList<>(sheet.getRows());
        }

        for (FilterCondition condition : conditions) {
            validateColumn(sheet, condition.getColumn());
        }

        return sheet.getRows().stream()
                .filter(row -> conditions.stream().allMatch(c -> matchesCondition(row, c, sheet)))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> sort(ExcelSheetData sheet, List<SortCondition> sortConditions) {
        validateSheet(sheet);
        if (sortConditions == null || sortConditions.isEmpty()) {
            return new ArrayList<>(sheet.getRows());
        }

        for (SortCondition sc : sortConditions) {
            validateColumn(sheet, sc.getColumn());
        }

        List<Map<String, Object>> result = new ArrayList<>(sheet.getRows());
        Comparator<Map<String, Object>> comparator = buildComparator(sortConditions, sheet);
        result.sort(comparator);
        return result;
    }

    private static class GroupAccumulator {
        int count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        long numCount = 0;
        BigDecimal min = null;
        BigDecimal max = null;

        void addCount() {
            count++;
        }

        void addValue(BigDecimal val) {
            count++;
            if (val != null) {
                numCount++;
                sum = sum.add(val);
                if (min == null || val.compareTo(min) < 0) min = val;
                if (max == null || val.compareTo(max) > 0) max = val;
            }
        }

        Object getAggregationValue(AggregationFunction fn) {
            if (fn == AggregationFunction.COUNT) {
                return (long) count;
            }
            if (numCount == 0) {
                return null;
            }
            return switch (fn) {
                case SUM -> sum;
                case AVERAGE -> sum.divide(BigDecimal.valueOf(numCount), 4, RoundingMode.HALF_UP);
                case MIN -> min;
                case MAX -> max;
                case COUNT -> (long) count;
            };
        }
    }

    @Override
    public GroupResult groupBy(ExcelSheetData sheet, String groupByColumn,
                               String aggregationColumn, AggregationFunction aggregation) {
        validateSheetAndColumn(sheet, groupByColumn);
        String resolvedGroup = sheet.resolveColumnName(groupByColumn);
        String resolvedAgg = null;

        if (aggregation != AggregationFunction.COUNT) {
            validateColumn(sheet, aggregationColumn);
            resolvedAgg = sheet.resolveColumnName(aggregationColumn);
        }

        Map<Object, GroupAccumulator> groups = new LinkedHashMap<>();
        boolean isCount = (aggregation == AggregationFunction.COUNT);
        List<Map<String, Object>> rows = sheet.getRows() != null ? sheet.getRows() : Collections.emptyList();

        for (Map<String, Object> row : rows) {
            Object rawKey = row.get(resolvedGroup);
            Object key = rawKey != null ? rawKey : "(Blank)";
            GroupAccumulator acc = groups.computeIfAbsent(key, k -> new GroupAccumulator());
            if (isCount) {
                acc.addCount();
            } else {
                Object val = row.get(resolvedAgg);
                acc.addValue(toBigDecimal(val));
            }
        }

        List<GroupResult.GroupEntry> entries = new ArrayList<>(groups.size());
        for (Map.Entry<Object, GroupAccumulator> entry : groups.entrySet()) {
            GroupAccumulator acc = entry.getValue();
            entries.add(GroupResult.GroupEntry.builder()
                    .key(entry.getKey())
                    .count(acc.count)
                    .aggregationValue(acc.getAggregationValue(aggregation))
                    .build());
        }

        return GroupResult.builder()
                .groupByColumn(resolvedGroup)
                .aggregationColumn(resolvedAgg)
                .aggregation(aggregation)
                .groups(entries)
                .build();
    }

    @Override
    public List<Map<String, Object>> findDuplicateRows(ExcelSheetData sheet) {
        validateSheet(sheet);
        return findDuplicatesByColumns(sheet, sheet.getHeaders());
    }

    @Override
    public List<Map<String, Object>> findDuplicatesByColumns(ExcelSheetData sheet, List<String> columns) {
        validateSheet(sheet);
        if (columns == null || columns.isEmpty()) {
            throw new AnalysisException("At least one column must be specified for duplicate detection");
        }

        List<String> resolved = columns.stream()
                .map(c -> {
                    validateColumn(sheet, c);
                    return sheet.resolveColumnName(c);
                })
                .toList();

        Map<String, List<Map<String, Object>>> fingerprints = new LinkedHashMap<>();
        for (Map<String, Object> row : sheet.getRows()) {
            String key = RowFingerprintUtil.computeFingerprint(row, resolved);
            fingerprints.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        return fingerprints.values().stream()
                .filter(list -> list.size() > 1)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    @Override
    public ColumnStatistics getColumnStatistics(ExcelSheetData sheet, String column) {
        validateSheetAndColumn(sheet, column);
        String resolved = sheet.resolveColumnName(column);

        List<Object> allValues = sheet.getRows().stream()
                .map(row -> row.get(resolved))
                .toList();

        long totalCount = allValues.size();
        long nullCount = allValues.stream().filter(Objects::isNull).count();
        List<Object> nonNullValues = allValues.stream().filter(Objects::nonNull).toList();
        long distinctCount = nonNullValues.stream().distinct().count();

        String dataType = detectColumnType(nonNullValues);

        ColumnStatistics.ColumnStatisticsBuilder builder = ColumnStatistics.builder()
                .column(resolved)
                .dataType(dataType)
                .count(totalCount)
                .nullCount(nullCount)
                .distinctCount(distinctCount);

        switch (dataType) {
            case "NUMERIC" -> {
                List<BigDecimal> nums = new ArrayList<>();
                long invalidCount = 0;
                for (Object v : nonNullValues) {
                    BigDecimal bd = tryParseBigDecimal(v);
                    if (bd != null) {
                        nums.add(bd);
                    } else {
                        invalidCount++;
                    }
                }
                if (invalidCount > 0) {
                    builder.invalidCount(invalidCount);
                }
                if (!nums.isEmpty()) {
                    BigDecimal total = nums.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                    builder.sum(total)
                            .average(total.divide(BigDecimal.valueOf(nums.size()), 4, RoundingMode.HALF_UP))
                            .min(nums.stream().min(Comparator.naturalOrder()).orElse(null))
                            .max(nums.stream().max(Comparator.naturalOrder()).orElse(null));
                }
            }
            case "DATE" -> {
                List<String> dates = nonNullValues.stream()
                        .map(Object::toString)
                        .sorted()
                        .toList();
                if (!dates.isEmpty()) {
                    builder.min(dates.getFirst())
                            .max(dates.getLast());
                }
            }
            case "STRING" -> {
                Map<String, Long> freq = nonNullValues.stream()
                        .map(Object::toString)
                        .collect(Collectors.groupingBy(s -> s, LinkedHashMap::new, Collectors.counting()));
                List<Map.Entry<String, Long>> topValues = freq.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(TOP_VALUES_LIMIT)
                        .toList();
                builder.topValues(topValues);
            }
            default -> {  }
        }

        return builder.build();
    }

    private void validateSheet(ExcelSheetData sheet) {
        if (sheet == null) {
            throw new AnalysisException("Sheet not found");
        }
    }

    private void validateColumn(ExcelSheetData sheet, String column) {
        if (column == null || column.isBlank()) {
            throw new AnalysisException("Column name must not be empty");
        }
        if (!sheet.hasColumn(column)) {
            throw new AnalysisException("Column not found: " + column
                    + ". Available columns: " + sheet.getHeaders());
        }
    }

    private void validateSheetAndColumn(ExcelSheetData sheet, String column) {
        validateSheet(sheet);
        validateColumn(sheet, column);
    }

    private List<BigDecimal> extractNumericValues(ExcelSheetData sheet, String column) {
        validateSheetAndColumn(sheet, column);
        String resolved = sheet.resolveColumnName(column);

        List<Object> nonNullValues = sheet.getRows().stream()
                .map(row -> row.get(resolved))
                .filter(Objects::nonNull)
                .toList();

        if (nonNullValues.isEmpty()) {
            return List.of();
        }

        List<BigDecimal> numericList = new ArrayList<>();
        int invalidCount = 0;
        for (Object val : nonNullValues) {
            BigDecimal bd = tryParseBigDecimal(val);
            if (bd != null) {
                numericList.add(bd);
            } else {
                invalidCount++;
            }
        }

        if (numericList.isEmpty()) {
            throw new AnalysisException("Column '" + resolved
                    + "' is not a numeric column. Cannot perform numeric operations on it.");
        }

        if (invalidCount > 0) {
            log.info("Column '{}' has mixed data: {} numeric values analyzed, {} invalid/non-numeric values ignored.",
                    resolved, numericList.size(), invalidCount);
        }

        return numericList;
    }

    private List<BigDecimal> toNumericList(List<Object> values) {
        List<BigDecimal> list = new ArrayList<>();
        for (Object v : values) {
            BigDecimal bd = tryParseBigDecimal(v);
            if (bd != null) {
                list.add(bd);
            }
        }
        return list;
    }

    private BigDecimal tryParseBigDecimal(Object val) {
        return NumericParser.parseBigDecimal(val);
    }

    private boolean matchesCondition(Map<String, Object> row, FilterCondition condition,
                                     ExcelSheetData sheet) {
        String resolved = sheet.resolveColumnName(condition.getColumn());
        Object cellValue = row.get(resolved);
        FilterOperator op = condition.getOperator();
        Object filterValue = condition.getValue();

        if (op == FilterOperator.IS_NULL) return cellValue == null;
        if (op == FilterOperator.IS_NOT_NULL) return cellValue != null;

        if (op == FilterOperator.GREATER_THAN || op == FilterOperator.GREATER_THAN_OR_EQUAL
                || op == FilterOperator.LESS_THAN || op == FilterOperator.LESS_THAN_OR_EQUAL) {
            if (cellValue == null || (cellValue instanceof String s && s.trim().isEmpty())) {
                return false;
            }
            if (filterValue == null || (filterValue instanceof String fs && fs.trim().isEmpty())) {
                return false;
            }
            Boolean dateMatch = matchDateCondition(cellValue, filterValue, op);
            if (dateMatch != null) {
                return dateMatch;
            }
            BigDecimal cellNum = NumericParser.parseBigDecimal(cellValue);
            BigDecimal filterNum = NumericParser.parseBigDecimal(filterValue);
            if (cellNum != null && filterNum != null) {
                int cmp = cellNum.compareTo(filterNum);
                return switch (op) {
                    case GREATER_THAN -> cmp > 0;
                    case GREATER_THAN_OR_EQUAL -> cmp >= 0;
                    case LESS_THAN -> cmp < 0;
                    case LESS_THAN_OR_EQUAL -> cmp <= 0;
                    default -> false;
                };
            }
        }

        Boolean dateMatch = matchDateCondition(cellValue, filterValue, op);
        if (dateMatch != null) {
            return dateMatch;
        }

        return switch (op) {
            case EQUALS -> objectEquals(cellValue, filterValue);
            case NOT_EQUALS -> !objectEquals(cellValue, filterValue);
            case GREATER_THAN -> compareValues(cellValue, filterValue) > 0;
            case GREATER_THAN_OR_EQUAL -> compareValues(cellValue, filterValue) >= 0;
            case LESS_THAN -> compareValues(cellValue, filterValue) < 0;
            case LESS_THAN_OR_EQUAL -> compareValues(cellValue, filterValue) <= 0;
            case CONTAINS -> stringOp(cellValue, filterValue,
                    (s, v) -> s.toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT)));
            case STARTS_WITH -> stringOp(cellValue, filterValue,
                    (s, v) -> s.toLowerCase(Locale.ROOT).startsWith(v.toLowerCase(Locale.ROOT)));
            case ENDS_WITH -> stringOp(cellValue, filterValue,
                    (s, v) -> s.toLowerCase(Locale.ROOT).endsWith(v.toLowerCase(Locale.ROOT)));
            default -> false;
        };
    }

    private Boolean matchDateCondition(Object cellValue, Object filterValue, FilterOperator op) {
        if (cellValue == null || filterValue == null) return null;
        LocalDateTime cellDt = tryParseDateTime(cellValue);
        if (cellDt == null) {
            LocalDate cDate = tryParseDate(cellValue);
            if (cDate != null) cellDt = cDate.atStartOfDay();
        }
        if (cellDt == null) return null;

        LocalDate fDate = tryParseDate(filterValue);
        LocalDateTime fDt = tryParseDateTime(filterValue);

        if (fDt == null && fDate != null) {
            LocalDateTime start = fDate.atStartOfDay();
            LocalDateTime end = fDate.atTime(LocalTime.MAX);
            return switch (op) {
                case EQUALS -> !cellDt.isBefore(start) && !cellDt.isAfter(end);
                case NOT_EQUALS -> cellDt.isBefore(start) || cellDt.isAfter(end);
                case GREATER_THAN -> cellDt.isAfter(end);
                case GREATER_THAN_OR_EQUAL -> !cellDt.isBefore(start);
                case LESS_THAN -> cellDt.isBefore(start);
                case LESS_THAN_OR_EQUAL -> !cellDt.isAfter(end);
                default -> null;
            };
        } else if (fDt != null) {
            int cmp = cellDt.compareTo(fDt);
            return switch (op) {
                case EQUALS -> cmp == 0;
                case NOT_EQUALS -> cmp != 0;
                case GREATER_THAN -> cmp > 0;
                case GREATER_THAN_OR_EQUAL -> cmp >= 0;
                case LESS_THAN -> cmp < 0;
                case LESS_THAN_OR_EQUAL -> cmp <= 0;
                default -> null;
            };
        }
        return null;
    }

    private LocalDate tryParseDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    private LocalDateTime tryParseDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDateTime ldt) return ldt;
        String s = val.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DATE_TIME_FMT);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        } catch (DateTimeParseException ignored) {}
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    private boolean objectEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        BigDecimal numA = NumericParser.parseBigDecimal(a);
        BigDecimal numB = NumericParser.parseBigDecimal(b);
        if (numA != null && numB != null) {
            return numA.compareTo(numB) == 0;
        }
        return a.toString().equalsIgnoreCase(b.toString());
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object cellValue, Object filterValue) {
        if (cellValue == null) return -1;
        if (filterValue == null) return 1;

        BigDecimal cellNum = NumericParser.parseBigDecimal(cellValue);
        BigDecimal filterNum = NumericParser.parseBigDecimal(filterValue);
        if (cellNum != null && filterNum != null) {
            return cellNum.compareTo(filterNum);
        }

        if (isDateString(cellValue.toString()) && isDateString(filterValue.toString())) {
            return cellValue.toString().compareTo(filterValue.toString());
        }

        return cellValue.toString().compareToIgnoreCase(filterValue.toString());
    }

    private boolean stringOp(Object cellValue, Object filterValue,
                             java.util.function.BiFunction<String, String, Boolean> op) {
        if (cellValue == null || filterValue == null) return false;
        return op.apply(cellValue.toString(), filterValue.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        return NumericParser.parseBigDecimal(value);
    }

    private boolean isDateString(String s) {
        try {
            LocalDateTime.parse(s, DATE_TIME_FMT);
            return true;
        } catch (DateTimeParseException e) {
            try {
                LocalDate.parse(s);
                return true;
            } catch (DateTimeParseException e2) {
                return false;
            }
        }
    }

    private Comparator<Map<String, Object>> buildComparator(List<SortCondition> conditions,
                                                             ExcelSheetData sheet) {
        Comparator<Map<String, Object>> comparator = null;

        for (SortCondition sc : conditions) {
            String resolved = sheet.resolveColumnName(sc.getColumn());
            Comparator<Map<String, Object>> colComparator = (r1, r2) -> {
                Object v1 = r1.get(resolved);
                Object v2 = r2.get(resolved);
                return compareForSort(v1, v2);
            };

            if (sc.getDirection() == SortDirection.DESC) {
                colComparator = colComparator.reversed();
            }

            comparator = (comparator == null) ? colComparator : comparator.thenComparing(colComparator);
        }

        return comparator;
    }

    private int compareForSort(Object a, Object b) {
        return NumericParser.compare(a, b);
    }

    private String detectColumnType(List<Object> nonNullValues) {
        if (nonNullValues.isEmpty()) return "UNKNOWN";

        int numericCount = 0;
        int booleanCount = 0;
        int dateCount = 0;
        int total = nonNullValues.size();

        for (Object val : nonNullValues) {
            if (NumericParser.isNumeric(val)) numericCount++;
            if (val instanceof Boolean || (val instanceof String s && ("true".equalsIgnoreCase(s.trim()) || "false".equalsIgnoreCase(s.trim())))) {
                booleanCount++;
            }
            if (val instanceof LocalDate || val instanceof LocalDateTime || (val instanceof String s && isDateString(s))) {
                dateCount++;
            }
        }

        if ((double) numericCount / total >= 0.50) return "NUMERIC";
        if ((double) booleanCount / total >= 0.80) return "BOOLEAN";
        if ((double) dateCount / total >= 0.70) return "DATE";
        return "STRING";
    }
}
