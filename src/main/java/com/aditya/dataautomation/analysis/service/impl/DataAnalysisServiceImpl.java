package com.aditya.dataautomation.analysis.service.impl;

import com.aditya.dataautomation.analysis.exception.AnalysisException;
import com.aditya.dataautomation.analysis.model.*;
import com.aditya.dataautomation.analysis.service.DataAnalysisService;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

        // Validate all columns up-front
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

        // Group rows by the key column
        Map<Object, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : sheet.getRows()) {
            Object key = row.get(resolvedGroup);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        List<GroupResult.GroupEntry> entries = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> entry : groups.entrySet()) {
            List<Map<String, Object>> groupRows = entry.getValue();
            Object aggValue = calculateAggregation(groupRows, resolvedAgg, aggregation);
            entries.add(GroupResult.GroupEntry.builder()
                    .key(entry.getKey())
                    .count(groupRows.size())
                    .aggregationValue(aggValue)
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

        // Build a fingerprint for each row based on selected columns
        Map<String, List<Map<String, Object>>> fingerprints = new LinkedHashMap<>();
        for (Map<String, Object> row : sheet.getRows()) {
            String key = resolved.stream()
                    .map(col -> String.valueOf(row.get(col)))
                    .collect(Collectors.joining("|"));
            fingerprints.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        // Return all rows whose fingerprint appears more than once
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
                List<BigDecimal> nums = toNumericList(nonNullValues);
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
                // Top N most frequent values
                Map<String, Long> freq = nonNullValues.stream()
                        .map(Object::toString)
                        .collect(Collectors.groupingBy(s -> s, LinkedHashMap::new, Collectors.counting()));
                List<Map.Entry<String, Long>> topValues = freq.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(TOP_VALUES_LIMIT)
                        .toList();
                builder.topValues(topValues);
            }
            default -> { /* BOOLEAN or UNKNOWN — just basic counts */ }
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


        boolean hasNumeric = false;
        boolean hasNonNumeric = false;
        for (Object val : nonNullValues) {
            if (val instanceof Number) {
                hasNumeric = true;
            } else {
                hasNonNumeric = true;
            }
        }

        if (!hasNumeric || hasNonNumeric) {
            throw new AnalysisException("Column '" + resolved
                    + "' is not a numeric column. Cannot perform numeric operations on it.");
        }

        return toNumericList(nonNullValues);
    }

    private List<BigDecimal> toNumericList(List<Object> values) {
        return values.stream()
                .filter(v -> v instanceof Number)
                .map(v -> {
                    if (v instanceof BigDecimal bd) return bd;
                    if (v instanceof Long l) return BigDecimal.valueOf(l);
                    if (v instanceof Integer i) return BigDecimal.valueOf(i);
                    if (v instanceof Double d) return BigDecimal.valueOf(d);
                    return new BigDecimal(v.toString());
                })
                .toList();
    }


    private boolean matchesCondition(Map<String, Object> row, FilterCondition condition,
                                     ExcelSheetData sheet) {
        String resolved = sheet.resolveColumnName(condition.getColumn());
        Object cellValue = row.get(resolved);
        FilterOperator op = condition.getOperator();
        Object filterValue = condition.getValue();

        return switch (op) {
            case IS_NULL -> cellValue == null;
            case IS_NOT_NULL -> cellValue != null;
            case EQUALS -> objectEquals(cellValue, filterValue);
            case NOT_EQUALS -> !objectEquals(cellValue, filterValue);
            case GREATER_THAN -> compareValues(cellValue, filterValue) > 0;
            case GREATER_THAN_OR_EQUAL -> compareValues(cellValue, filterValue) >= 0;
            case LESS_THAN -> compareValues(cellValue, filterValue) < 0;
            case LESS_THAN_OR_EQUAL -> compareValues(cellValue, filterValue) <= 0;
            case CONTAINS -> stringOp(cellValue, filterValue,
                    (s, v) -> s.toLowerCase().contains(v.toLowerCase()));
            case STARTS_WITH -> stringOp(cellValue, filterValue,
                    (s, v) -> s.toLowerCase().startsWith(v.toLowerCase()));
            case ENDS_WITH -> stringOp(cellValue, filterValue,
                    (s, v) -> s.toLowerCase().endsWith(v.toLowerCase()));
        };
    }

    private boolean objectEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        if (a instanceof Number && b instanceof Number) {
            return toBigDecimal(a).compareTo(toBigDecimal(b)) == 0;
        }
        return a.toString().equalsIgnoreCase(b.toString());
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object cellValue, Object filterValue) {
        if (cellValue == null) return -1;
        if (filterValue == null) return 1;


        if (cellValue instanceof Number && filterValue instanceof Number) {
            return toBigDecimal(cellValue).compareTo(toBigDecimal(filterValue));
        }


        if (cellValue instanceof Number && filterValue instanceof String fs) {
            try {
                return toBigDecimal(cellValue).compareTo(new BigDecimal(fs));
            } catch (NumberFormatException e) {

            }
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
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Long l) return BigDecimal.valueOf(l);
        if (value instanceof Integer i) return BigDecimal.valueOf(i);
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        return new BigDecimal(value.toString());
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
        if (a == null && b == null) return 0;
        if (a == null) return 1;   // nulls last
        if (b == null) return -1;

        if (a instanceof Number && b instanceof Number) {
            return toBigDecimal(a).compareTo(toBigDecimal(b));
        }

        return a.toString().compareToIgnoreCase(b.toString());
    }


    private Object calculateAggregation(List<Map<String, Object>> rows, String column,
                                        AggregationFunction function) {
        if (function == AggregationFunction.COUNT) {
            return (long) rows.size();
        }

        List<BigDecimal> numericValues = rows.stream()
                .map(row -> row.get(column))
                .filter(v -> v instanceof Number)
                .map(this::toBigDecimal)
                .toList();

        if (numericValues.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return switch (function) {
            case SUM -> numericValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVERAGE -> {
                BigDecimal total = numericValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                yield total.divide(BigDecimal.valueOf(numericValues.size()), 4, RoundingMode.HALF_UP);
            }
            case MIN -> numericValues.stream().min(Comparator.naturalOrder()).orElse(null);
            case MAX -> numericValues.stream().max(Comparator.naturalOrder()).orElse(null);
            case COUNT -> (long) rows.size();
        };
    }


    private String detectColumnType(List<Object> nonNullValues) {
        if (nonNullValues.isEmpty()) return "UNKNOWN";

        boolean allNumeric = true;
        boolean allBoolean = true;
        boolean allDate = true;

        for (Object val : nonNullValues) {
            if (!(val instanceof Number)) allNumeric = false;
            if (!(val instanceof Boolean)) allBoolean = false;
            if (val instanceof String s) {
                if (!isDateString(s)) allDate = false;
            } else if (!(val instanceof LocalDate || val instanceof LocalDateTime)) {
                allDate = false;
            }
        }

        if (allNumeric) return "NUMERIC";
        if (allBoolean) return "BOOLEAN";
        if (allDate) return "DATE";
        return "STRING";
    }
}
