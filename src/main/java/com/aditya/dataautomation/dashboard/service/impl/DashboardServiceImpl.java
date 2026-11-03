package com.aditya.dataautomation.dashboard.service.impl;

import com.aditya.dataautomation.dashboard.exception.DashboardException;
import com.aditya.dataautomation.dashboard.model.*;
import com.aditya.dataautomation.dashboard.service.DashboardService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Set<String> VALID_AGGREGATIONS =
            Set.of("COUNT", "SUM", "AVERAGE", "MIN", "MAX");

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*");
    private static final Pattern ALT_DATE_PATTERN = Pattern.compile("^\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}.*");
    private static final Pattern ID_COLUMN_PATTERN = Pattern.compile("^(id|.*_id|.*id|code|.*_code|zip|postal|ssn|phone|isbn|key)$");
    private static final Pattern METRIC_NAME_STRONG = Pattern.compile(".*(revenue|sales|profit|amount|total|cost|price|income|budget|spend|balance|turnover).*");
    private static final Pattern METRIC_NAME_MODERATE = Pattern.compile(".*(quantity|units|score|rating|discount|rate|weight|hours|count|target).*");
    private static final Pattern DIMENSION_NAME = Pattern.compile(".*(category|region|segment|status|country|city|state|product|department|brand|customer|channel|gender|type|tier).*");
    private static final Pattern AVERAGE_METRIC_PATTERN = Pattern.compile(".*(price|rate|unitprice|average|avg|score|rating|percentage|percent).*");
    private static final Pattern SUM_METRIC_PATTERN = Pattern.compile(".*(amount|total|quantity|units|revenue|sales|cost|count|sum).*");

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MM-dd-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    };

    private static final int MAX_DASHBOARDS = 200;
    private final Map<String, Dashboard> dashboards = new ConcurrentHashMap<>();

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 3600000)
    public void cleanupExpiredDashboards() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        dashboards.entrySet().removeIf(entry -> {
            Dashboard d = entry.getValue();
            return d.getCreatedAt() != null && d.getCreatedAt().isBefore(cutoff);
        });
    }

    private void evictOldestIfFull() {
        if (dashboards.size() >= MAX_DASHBOARDS) {
            cleanupExpiredDashboards();
            if (dashboards.size() >= MAX_DASHBOARDS) {

                dashboards.entrySet().stream()
                        .filter(e -> e.getValue().getCreatedAt() != null)
                        .min(Comparator.comparing(e -> e.getValue().getCreatedAt()))
                        .map(Map.Entry::getKey)
                        .ifPresent(dashboards::remove);
            }
        }
    }

    @Override
    public Dashboard createDashboard(Long userId, String fileId, String fileName, String name, String sheetName, ExcelWorkbookData workbook) {
        validateWorkbook(workbook);
        if (name == null || name.isBlank()) {
            throw new DashboardException("Dashboard name must not be empty");
        }
        resolveSheet(workbook, sheetName);
        evictOldestIfFull();

        Dashboard dashboard = Dashboard.builder()
                .dashboardId(UUID.randomUUID().toString())
                .userId(userId != null ? userId : 0L)
                .fileId(fileId)
                .fileName(fileName != null ? fileName : (workbook.getFileName() != null ? workbook.getFileName() : "Active Workbook"))
                .name(name)
                .sheetName(sheetName)
                .createdAt(LocalDateTime.now())
                .build();

        dashboards.put(dashboard.getDashboardId(), dashboard);
        log.info("Created dashboard '{}' (id={}) for userId={}, fileId={}", name, dashboard.getDashboardId(), userId, fileId);
        return dashboard;
    }

    @Override
    public Dashboard createDashboard(String name, String sheetName, ExcelWorkbookData workbook) {
        String fileName = workbook != null ? workbook.getFileName() : null;
        return createDashboard(0L, null, fileName, name, sheetName, workbook);
    }

    @Override
    public DashboardWidget addWidget(Long userId, String dashboardId, DashboardWidget widget, ExcelWorkbookData workbook) {
        validateWorkbook(workbook);
        Dashboard dashboard = getDashboardAndVerifyOwnership(userId, dashboardId);

        if (widget.getType() == null) {
            throw new DashboardException("Widget type is required");
        }

        widget.setWidgetId(UUID.randomUUID().toString());
        if (widget.getSheetName() == null) {
            widget.setSheetName(dashboard.getSheetName());
        }

        ExcelSheetData sheet = resolveSheet(workbook, widget.getSheetName());
        validateWidgetConfig(widget, sheet);
        computeWidgetData(widget, sheet);

        dashboard.addWidget(widget);
        log.info("Added {} widget '{}' to dashboard '{}' by userId={}", widget.getType(), widget.getTitle(), dashboardId, userId);
        return widget;
    }

    @Override
    public DashboardWidget addWidget(String dashboardId, DashboardWidget widget, ExcelWorkbookData workbook) {
        return addWidget(null, dashboardId, widget, workbook);
    }

    @Override
    public void removeWidget(Long userId, String dashboardId, String widgetId) {
        Dashboard dashboard = getDashboardAndVerifyOwnership(userId, dashboardId);
        boolean removed = dashboard.removeWidget(widgetId);
        if (!removed) {
            log.warn("Widget '{}' not found or already removed from dashboard '{}' by userId={}", widgetId, dashboardId, userId);
            throw new DashboardException("Widget not found: " + widgetId);
        }
        log.info("Removed widget '{}' from dashboard '{}' by userId={}", widgetId, dashboardId, userId);
    }

    @Override
    public void removeWidget(String dashboardId, String widgetId) {
        removeWidget(null, dashboardId, widgetId);
    }

    @Override
    public Dashboard getDashboard(Long userId, String dashboardId, ExcelWorkbookData workbook) {
        validateWorkbook(workbook);
        Dashboard dashboard = getDashboardAndVerifyOwnership(userId, dashboardId);

        List<DashboardWidget> widgets = new ArrayList<>(dashboard.getWidgets());
        for (DashboardWidget widget : widgets) {
            ExcelSheetData sheet = resolveSheet(workbook, widget.getSheetName());
            computeWidgetData(widget, sheet);
        }

        return dashboard;
    }

    @Override
    public Dashboard getDashboard(String dashboardId, ExcelWorkbookData workbook) {
        return getDashboard(null, dashboardId, workbook);
    }

    @Override
    public Dashboard findDashboard(Long userId, String dashboardId) {
        return getDashboardAndVerifyOwnership(userId, dashboardId);
    }

    @Override
    public List<Dashboard> listDashboards(Long userId) {
        if (userId == null) userId = 0L;
        final Long uid = userId;
        return dashboards.values().stream()
                .filter(d -> Objects.equals(d.getUserId(), uid))
                .sorted(Comparator.comparing(Dashboard::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteDashboard(Long userId, String dashboardId) {
        Dashboard dashboard = getDashboardAndVerifyOwnership(userId, dashboardId);
        dashboards.remove(dashboard.getDashboardId());
        log.info("Deleted dashboard '{}' (id={}) by userId={}", dashboard.getName(), dashboardId, userId);
    }

    private Dashboard getDashboardAndVerifyOwnership(Long userId, String dashboardId) {
        if (dashboardId == null || dashboardId.isBlank()) {
            throw new DashboardException("Dashboard ID must not be empty");
        }
        Dashboard d = dashboards.get(dashboardId);
        if (d == null) {
            throw new DashboardException("Dashboard not found: " + dashboardId);
        }

        if (userId != null && !Objects.equals(d.getUserId(), userId)) {
            log.warn("Access denied: userId={} attempted to access dashboardId={} owned by userId={}",
                    userId, dashboardId, d.getUserId());
            throw new DashboardException("Dashboard not found or access denied");
        }
        return d;
    }

    private void validateWidgetConfig(DashboardWidget widget, ExcelSheetData sheet) {
        switch (widget.getType()) {
            case KPI -> validateKpiWidget(widget, sheet);
            case TABLE -> validateTableWidget(widget, sheet);
            case BAR_CHART, LINE_CHART, PIE_CHART, AREA_CHART, DONUT_CHART -> validateChartWidget(widget, sheet);
        }
    }

    private void validateKpiWidget(DashboardWidget widget, ExcelSheetData sheet) {
        if (widget.getAggregation() == null || widget.getAggregation().isBlank()) {
            throw new DashboardException("KPI widget requires an aggregation (COUNT, SUM, AVERAGE, MIN, MAX)");
        }
        String agg = widget.getAggregation().toUpperCase();
        if (!VALID_AGGREGATIONS.contains(agg)) {
            throw new DashboardException("Invalid aggregation: '" + widget.getAggregation()
                    + "'. Supported: " + VALID_AGGREGATIONS);
        }
        if (!"COUNT".equals(agg)) {
            if (widget.getColumnName() == null || widget.getColumnName().isBlank()) {
                throw new DashboardException("KPI widget with " + agg + " requires a columnName");
            }
            resolveColumn(sheet, widget.getColumnName());
            validateNumericColumn(sheet, widget.getColumnName());
        }
    }

    private void validateTableWidget(DashboardWidget widget, ExcelSheetData sheet) {
        if (widget.getColumns() == null || widget.getColumns().isEmpty()) {
            throw new DashboardException("TABLE widget requires at least one column");
        }
        for (String col : widget.getColumns()) {
            resolveColumn(sheet, col);
        }
    }

    private void validateChartWidget(DashboardWidget widget, ExcelSheetData sheet) {
        ChartConfig config = widget.getChartConfig();
        if (config == null) {
            throw new DashboardException(widget.getType() + " widget requires a chartConfig");
        }
        if (config.getCategoryColumn() == null || config.getCategoryColumn().isBlank()) {
            throw new DashboardException(widget.getType() + " requires chartConfig.categoryColumn");
        }
        if (config.getValueColumn() == null || config.getValueColumn().isBlank()) {
            throw new DashboardException(widget.getType() + " requires chartConfig.valueColumn");
        }
        if (config.getAggregation() == null || config.getAggregation().isBlank()) {
            throw new DashboardException(widget.getType() + " requires chartConfig.aggregation");
        }
        String agg = config.getAggregation().toUpperCase();
        if (!VALID_AGGREGATIONS.contains(agg)) {
            throw new DashboardException("Invalid chart aggregation: '" + config.getAggregation()
                    + "'. Supported: " + VALID_AGGREGATIONS);
        }
        resolveColumn(sheet, config.getCategoryColumn());
        resolveColumn(sheet, config.getValueColumn());
        if (!"COUNT".equals(agg)) {
            validateNumericColumn(sheet, config.getValueColumn());
        }
    }

    private void computeWidgetData(DashboardWidget widget, ExcelSheetData sheet) {
        switch (widget.getType()) {
            case KPI -> computeKpi(widget, sheet);
            case TABLE -> computeTable(widget, sheet);
            case BAR_CHART, LINE_CHART, PIE_CHART, AREA_CHART, DONUT_CHART -> computeChart(widget, sheet);
        }
    }

    private void computeKpi(DashboardWidget widget, ExcelSheetData sheet) {
        String agg = widget.getAggregation().toUpperCase();

        if ("COUNT".equals(agg)) {
            widget.setKpiValue((long) sheet.getRowCount());
            calculatePeriodGrowthForCount(widget, sheet);
            return;
        }

        String column = sheet.resolveColumnName(widget.getColumnName());
        List<Map<String, Object>> rows = sheet.getRows();
        if (rows == null || rows.isEmpty()) {
            widget.setKpiValue(BigDecimal.ZERO);
            widget.setAlertStatus("NORMAL");
            return;
        }

        int totalRows = rows.size();
        int mid = totalRows / 2;

        double sumPrior = 0.0;
        long countPrior = 0;
        double minPrior = Double.MAX_VALUE;
        double maxPrior = -Double.MAX_VALUE;

        double sumRecent = 0.0;
        long countRecent = 0;
        double minRecent = Double.MAX_VALUE;
        double maxRecent = -Double.MAX_VALUE;

        for (int i = 0; i < totalRows; i++) {
            Object raw = rows.get(i).get(column);
            if (raw == null) continue;

            double d;
            if (raw instanceof Number n) {
                d = n.doubleValue();
            } else {
                Double parsed = parseQuickDouble(raw);
                if (parsed == null) continue;
                d = parsed;
            }

            if (i < mid) {
                sumPrior += d;
                countPrior++;
                if (d < minPrior) minPrior = d;
                if (d > maxPrior) maxPrior = d;
            } else {
                sumRecent += d;
                countRecent++;
                if (d < minRecent) minRecent = d;
                if (d > maxRecent) maxRecent = d;
            }
        }

        long totalCount = countPrior + countRecent;
        if (totalCount == 0) {
            widget.setKpiValue(BigDecimal.ZERO);
            widget.setAlertStatus("NORMAL");
            return;
        }

        double totalSum = sumPrior + sumRecent;
        double computedVal = switch (agg) {
            case "SUM" -> totalSum;
            case "AVERAGE" -> totalSum / totalCount;
            case "MIN" -> Math.min(minPrior, minRecent);
            case "MAX" -> Math.max(maxPrior, maxRecent);
            default -> totalSum;
        };

        BigDecimal kpiVal = BigDecimal.valueOf(computedVal).setScale(2, RoundingMode.HALF_UP);
        widget.setKpiValue(kpiVal);

        if (countPrior > 0 && countRecent > 0) {
            double priorAggVal = switch (agg) {
                case "SUM" -> sumPrior;
                case "AVERAGE" -> sumPrior / countPrior;
                case "MIN" -> minPrior;
                case "MAX" -> maxPrior;
                default -> sumPrior;
            };

            double recentAggVal = switch (agg) {
                case "SUM" -> sumRecent;
                case "AVERAGE" -> sumRecent / countRecent;
                case "MIN" -> minRecent;
                case "MAX" -> maxRecent;
                default -> sumRecent;
            };

            if (priorAggVal > 0) {
                double growth = ((recentAggVal - priorAggVal) / priorAggVal) * 100.0;
                setGrowthFields(widget, growth);
            }
        }

        if (widget.getGrowthRate() != null) {
            if (widget.getGrowthRate() >= 5.0) {
                widget.setAlertStatus("SUCCESS");
            } else if (widget.getGrowthRate() <= -5.0) {
                widget.setAlertStatus("WARNING");
            } else {
                widget.setAlertStatus("NORMAL");
            }
        } else {
            widget.setAlertStatus("NORMAL");
        }
    }

    private void calculatePeriodGrowthForCount(DashboardWidget widget, ExcelSheetData sheet) {
        int totalRows = sheet.getRowCount();
        if (totalRows >= 2) {
            int mid = totalRows / 2;
            int prior = mid;
            int recent = totalRows - mid;
            if (prior > 0) {
                double growth = ((double) (recent - prior) / prior) * 100.0;
                setGrowthFields(widget, growth);
            }
        }
        widget.setAlertStatus("NORMAL");
    }

    private void setGrowthFields(DashboardWidget widget, double growth) {
        double rounded = Math.round(growth * 10.0) / 10.0;
        widget.setGrowthRate(rounded);
        if (rounded > 0.1) {
            widget.setGrowthTrend("UP");
        } else if (rounded < -0.1) {
            widget.setGrowthTrend("DOWN");
        } else {
            widget.setGrowthTrend("FLAT");
        }
    }

    private void computeTable(DashboardWidget widget, ExcelSheetData sheet) {
        List<String> resolved = widget.getColumns().stream()
                .map(sheet::resolveColumnName)
                .toList();

        List<Map<String, Object>> sheetRows = sheet.getRows() != null ? sheet.getRows() : Collections.emptyList();
        int limit = Math.min(sheetRows.size(), 100);
        List<Map<String, Object>> tableRows = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = sheetRows.get(i);
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (String col : resolved) {
                filtered.put(col, row.get(col));
            }
            tableRows.add(filtered);
        }
        widget.setTableData(tableRows);
    }

    private static class GroupAcc {
        double sum = 0.0;
        long count = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        void add(double v) {
            sum += v;
            count++;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        void addCount() {
            count++;
        }

        Object getResult(String agg) {
            if ("COUNT".equals(agg)) return count;
            if (count == 0) return 0.0;
            return switch (agg) {
                case "SUM" -> Math.round(sum * 100.0) / 100.0;
                case "AVERAGE" -> Math.round((sum / count) * 100.0) / 100.0;
                case "MIN" -> min == Double.MAX_VALUE ? 0.0 : Math.round(min * 100.0) / 100.0;
                case "MAX" -> max == -Double.MAX_VALUE ? 0.0 : Math.round(max * 100.0) / 100.0;
                default -> Math.round(sum * 100.0) / 100.0;
            };
        }
    }

    private record PointWithTime(ChartConfig.ChartDataPoint point, Long timeMillis) {}

    private void computeChart(DashboardWidget widget, ExcelSheetData sheet) {
        ChartConfig config = widget.getChartConfig();
        String catCol = sheet.resolveColumnName(config.getCategoryColumn());
        String valCol = sheet.resolveColumnName(config.getValueColumn());
        String agg = config.getAggregation() != null ? config.getAggregation().toUpperCase() : "SUM";
        boolean isCount = "COUNT".equals(agg);
        boolean mergeSimilar = Boolean.TRUE.equals(config.getMergeSimilarCategories());

        Map<Object, GroupAcc> groups = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> casingCounts = mergeSimilar ? new LinkedHashMap<>() : null;
        List<Map<String, Object>> rows = sheet.getRows() != null ? sheet.getRows() : Collections.emptyList();

        for (Map<String, Object> row : rows) {
            Object rawKey = row.get(catCol);
            Object groupKey;
            if (rawKey == null) {
                groupKey = "(Blank)";
            } else if (mergeSimilar) {
                String rawStr = rawKey.toString().trim();
                if (rawStr.isEmpty()) {
                    rawStr = "(Blank)";
                }
                String norm = rawStr.toLowerCase();
                groupKey = norm;
                Map<String, Integer> counts = casingCounts.computeIfAbsent(norm, k -> new LinkedHashMap<>());
                counts.put(rawStr, counts.getOrDefault(rawStr, 0) + 1);
            } else {
                groupKey = rawKey;
            }

            GroupAcc acc = groups.computeIfAbsent(groupKey, k -> new GroupAcc());
            if (isCount) {
                acc.addCount();
            } else {
                Object val = row.get(valCol);
                if (val instanceof Number n) {
                    acc.add(n.doubleValue());
                } else if (val != null) {
                    Double d = parseQuickDouble(val);
                    if (d != null) acc.add(d);
                }
            }
        }

        List<ChartConfig.ChartDataPoint> points = new ArrayList<>(groups.size());
        for (Map.Entry<Object, GroupAcc> entry : groups.entrySet()) {
            Object displayLabel = entry.getKey();
            if (mergeSimilar && casingCounts != null) {
                Map<String, Integer> counts = casingCounts.get(entry.getKey());
                if (counts != null && !counts.isEmpty()) {
                    String dominant = null;
                    int maxCount = -1;
                    for (Map.Entry<String, Integer> variant : counts.entrySet()) {
                        if (variant.getValue() > maxCount) {
                            maxCount = variant.getValue();
                            dominant = variant.getKey();
                        }
                    }
                    if (dominant != null) {
                        displayLabel = dominant;
                    }
                }
            }
            points.add(ChartConfig.ChartDataPoint.builder()
                    .label(displayLabel)
                    .value(entry.getValue().getResult(agg))
                    .build());
        }

        if (points.size() > 12 && (widget.getType() == WidgetType.BAR_CHART
                || widget.getType() == WidgetType.PIE_CHART
                || widget.getType() == WidgetType.DONUT_CHART)) {
            points.sort((a, b) -> {
                double valA = a.getValue() instanceof Number n ? n.doubleValue() : 0.0;
                double valB = b.getValue() instanceof Number n ? n.doubleValue() : 0.0;
                return Double.compare(valB, valA);
            });
            List<ChartConfig.ChartDataPoint> top10 = new ArrayList<>(points.subList(0, 10));
            List<ChartConfig.ChartDataPoint> remainder = points.subList(10, points.size());

            double sumOthers = 0.0;
            long countOthers = 0;
            Double minOthers = null;
            Double maxOthers = null;

            for (ChartConfig.ChartDataPoint p : remainder) {
                if (p.getValue() instanceof Number n) {
                    double d = n.doubleValue();
                    sumOthers += d;
                    countOthers++;
                    if (minOthers == null || d < minOthers) minOthers = d;
                    if (maxOthers == null || d > maxOthers) maxOthers = d;
                }
            }

            if (countOthers > 0) {
                Object otherVal;
                if ("AVERAGE".equalsIgnoreCase(agg)) {
                    otherVal = Math.round((sumOthers / countOthers) * 100.0) / 100.0;
                } else if ("MIN".equalsIgnoreCase(agg)) {
                    otherVal = minOthers != null ? Math.round(minOthers * 100.0) / 100.0 : 0.0;
                } else if ("MAX".equalsIgnoreCase(agg)) {
                    otherVal = maxOthers != null ? Math.round(maxOthers * 100.0) / 100.0 : 0.0;
                } else {
                    otherVal = Math.round(sumOthers * 100.0) / 100.0;
                }

                top10.add(ChartConfig.ChartDataPoint.builder()
                        .label("Other (" + remainder.size() + " categories)")
                        .value(otherVal)
                        .build());
            }
            points = top10;
        }

        if (widget.getType() == WidgetType.AREA_CHART || widget.getType() == WidgetType.LINE_CHART) {
            List<PointWithTime> timedPoints = new ArrayList<>(points.size());
            int validDates = 0;
            for (ChartConfig.ChartDataPoint p : points) {
                Long t = parseDateToMillis(p.getLabel());
                if (t != null) validDates++;
                timedPoints.add(new PointWithTime(p, t));
            }

            if (validDates > 0) {
                timedPoints.sort((a, b) -> {
                    if (a.timeMillis != null && b.timeMillis != null) return a.timeMillis.compareTo(b.timeMillis);
                    if (a.timeMillis != null) return -1;
                    if (b.timeMillis != null) return 1;
                    return 0;
                });
                points = timedPoints.stream().map(PointWithTime::point).collect(Collectors.toList());
            }

            if (points.size() > 50) {
                points = downsampleTimeSeries(points, 40, agg);
            }
        }

        config.setDataPoints(points);
    }

    private Long parseDateToMillis(Object val) {
        if (val == null) return null;
        if (val instanceof java.util.Date d) return d.getTime();
        if (val instanceof TemporalAccessor) {
            if (val instanceof java.time.LocalDate ld) return ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            if (val instanceof java.time.LocalDateTime ldt) return ldt.toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
            if (val instanceof java.time.Instant inst) return inst.toEpochMilli();
        }
        if (val instanceof Number n) {
            double d = n.doubleValue();
            if (d >= 20000 && d <= 70000) {
                return Math.round((d - 25569.0) * 86400000.0);
            }
            if (d > 100000000 && d < 2500000000L) {
                return (long) (d * 1000);
            }
            if (d >= 2500000000L) {
                return (long) d;
            }
            return null;
        }
        String s = val.toString().trim();
        if (s.isEmpty() || "(Blank)".equalsIgnoreCase(s) || s.length() < 6) return null;

        char c0 = s.charAt(0);
        if (c0 < '0' || c0 > '9') {

            if (!Character.isLetter(c0)) return null;
        }

        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {}
        try {
            return java.time.LocalDateTime.parse(s).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        } catch (Exception ignored) {}
        try {
            return java.time.LocalDate.parse(s).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (Exception ignored) {}

        for (DateTimeFormatter dtf : DATE_FORMATTERS) {
            try {
                try {
                    return java.time.LocalDate.parse(s, dtf).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
                } catch (Exception e) {
                    return java.time.LocalDateTime.parse(s, dtf).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private List<ChartConfig.ChartDataPoint> downsampleTimeSeries(List<ChartConfig.ChartDataPoint> original, int targetBuckets, String agg) {
        if (original == null || original.size() <= targetBuckets) {
            return original;
        }
        List<ChartConfig.ChartDataPoint> sampled = new ArrayList<>(targetBuckets);
        double bucketSize = (double) original.size() / targetBuckets;

        for (int b = 0; b < targetBuckets; b++) {
            int start = (int) Math.round(b * bucketSize);
            int end = (int) Math.min(original.size(), Math.round((b + 1) * bucketSize));
            if (start >= end) start = Math.max(0, end - 1);
            List<ChartConfig.ChartDataPoint> chunk = original.subList(start, end);

            String label = String.valueOf(chunk.get(chunk.size() / 2).getLabel());
            double sum = 0.0;
            int count = 0;
            Double min = null;
            Double max = null;

            for (ChartConfig.ChartDataPoint p : chunk) {
                if (p.getValue() instanceof Number n) {
                    double d = n.doubleValue();
                    sum += d;
                    count++;
                    if (min == null || d < min) min = d;
                    if (max == null || d > max) max = d;
                }
            }

            Object bucketVal;
            if (count == 0) {
                bucketVal = 0.0;
            } else if ("AVERAGE".equalsIgnoreCase(agg)) {
                bucketVal = Math.round((sum / count) * 100.0) / 100.0;
            } else if ("MIN".equalsIgnoreCase(agg)) {
                bucketVal = min != null ? Math.round(min * 100.0) / 100.0 : 0.0;
            } else if ("MAX".equalsIgnoreCase(agg)) {
                bucketVal = max != null ? Math.round(max * 100.0) / 100.0 : 0.0;
            } else {
                bucketVal = Math.round(sum * 100.0) / 100.0;
            }

            sampled.add(ChartConfig.ChartDataPoint.builder()
                    .label(label)
                    .value(bucketVal)
                    .build());
        }
        return sampled;
    }

    private void validateWorkbook(ExcelWorkbookData workbook) {
        if (workbook == null) {
            throw new DashboardException("No workbook loaded. Upload an Excel file first.");
        }
    }

    private ExcelSheetData resolveSheet(ExcelWorkbookData workbook, String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new DashboardException("Sheet name must not be empty");
        }
        ExcelSheetData sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            List<String> available = workbook.getSheets() != null
                    ? workbook.getSheets().stream().map(ExcelSheetData::getSheetName).toList()
                    : Collections.emptyList();
            throw new DashboardException("Sheet not found: '" + sheetName + "'. Available: " + available);
        }
        return sheet;
    }

    private String resolveColumn(ExcelSheetData sheet, String column) {
        if (column == null || column.isBlank()) {
            throw new DashboardException("Column name must not be empty");
        }
        String resolved = sheet.resolveColumnName(column);
        if (resolved == null) {
            throw new DashboardException("Column not found: '" + column + "'. Available: " + sheet.getHeaders());
        }
        return resolved;
    }

    private void validateNumericColumn(ExcelSheetData sheet, String column) {
        String resolved = sheet.resolveColumnName(column);
        List<Map<String, Object>> rows = sheet.getRows();
        if (rows == null || rows.isEmpty()) return;

        int limit = Math.min(rows.size(), 200);
        for (int i = 0; i < limit; i++) {
            Object val = rows.get(i).get(resolved);
            if (isQuickNumeric(val)) return;
        }

        for (Map<String, Object> r : rows) {
            Object val = r.get(resolved);
            if (isQuickNumeric(val)) return;
        }

        throw new DashboardException("Column '" + column + "' does not contain numeric values. "
                + "Cannot perform numeric aggregation.");
    }

    @Override
    public Dashboard autoGenerateDashboard(Long userId, String fileId, String fileName, String name, String sheetName, ExcelWorkbookData workbook) {
        validateWorkbook(workbook);
        String finalName = (name != null && !name.isBlank()) ? name : (sheetName + " Executive Dashboard");
        Dashboard dashboard = createDashboard(userId, fileId, fileName, finalName, sheetName, workbook);
        ExcelSheetData sheet = resolveSheet(workbook, sheetName);

        if (sheet.getRowCount() == 0 || sheet.getHeaders() == null || sheet.getHeaders().isEmpty()) {
            DashboardWidget emptyKpi = DashboardWidget.builder()
                    .widgetId(UUID.randomUUID().toString())
                    .type(WidgetType.KPI)
                    .title("Total Records")
                    .sheetName(sheetName)
                    .aggregation("COUNT")
                    .kpiValue(0L)
                    .build();
            dashboard.addWidget(emptyKpi);
            return dashboard;
        }

        List<ColumnProfile> profiles = profileSheetColumns(sheet);

        List<ColumnProfile> numericCols = profiles.stream()
                .filter(p -> p.isNumeric && !p.isIdLike)
                .sorted(Comparator.comparingInt(p -> -p.metricScore))
                .toList();

        List<ColumnProfile> dateCols = profiles.stream()
                .filter(p -> p.isDate)
                .toList();

        List<ColumnProfile> categoricalCols = profiles.stream()
                .filter(p -> (p.isCategorical || (p.isNumeric && p.distinctCount <= 5 && !p.isIdLike)))
                .sorted(Comparator.comparingInt(p -> -p.dimensionScore))
                .toList();

        ColumnProfile primaryMetric = !numericCols.isEmpty() ? numericCols.get(0) : null;
        ColumnProfile secondaryMetric = numericCols.size() > 1 ? numericCols.get(1) : null;
        ColumnProfile tertiaryMetric = numericCols.size() > 2 ? numericCols.get(2) : null;

        addWidgetQuietly(dashboard, DashboardWidget.builder()
                .type(WidgetType.KPI)
                .title("Total Records")
                .sheetName(sheetName)
                .aggregation("COUNT")
                .build(), sheet);

        if (primaryMetric != null) {
            String agg = determineDefaultAggregation(primaryMetric.name);
            String prefix = "AVERAGE".equalsIgnoreCase(agg) ? "Average" : "Total";
            String title = formatKpiTitle(prefix, primaryMetric.name);
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title(title)
                    .sheetName(sheetName)
                    .columnName(primaryMetric.name)
                    .aggregation(agg)
                    .build(), sheet);
        }

        if (secondaryMetric != null) {
            String agg = determineDefaultAggregation(secondaryMetric.name);
            String prefix = "AVERAGE".equalsIgnoreCase(agg) ? "Average" : "Total";
            String title = formatKpiTitle(prefix, secondaryMetric.name);
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title(title)
                    .sheetName(sheetName)
                    .columnName(secondaryMetric.name)
                    .aggregation(agg)
                    .build(), sheet);
        } else if (primaryMetric != null && sheet.getRowCount() > 1) {
            String title = formatKpiTitle("Average", primaryMetric.name);
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title(title)
                    .sheetName(sheetName)
                    .columnName(primaryMetric.name)
                    .aggregation("AVERAGE")
                    .build(), sheet);
        }

        if (tertiaryMetric != null) {
            String agg = determineDefaultAggregation(tertiaryMetric.name);
            String prefix = "AVERAGE".equalsIgnoreCase(agg) ? "Average" : "Total";
            String title = formatKpiTitle(prefix, tertiaryMetric.name);
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title(title)
                    .sheetName(sheetName)
                    .columnName(tertiaryMetric.name)
                    .aggregation(agg)
                    .build(), sheet);
        } else if (secondaryMetric != null && sheet.getRowCount() > 1) {
            String title = formatKpiTitle("Average", secondaryMetric.name);
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title(title)
                    .sheetName(sheetName)
                    .columnName(secondaryMetric.name)
                    .aggregation("AVERAGE")
                    .build(), sheet);
        }

        Set<String> usedChartSignatures = new HashSet<>();

        if (!dateCols.isEmpty() && primaryMetric != null) {
            ColumnProfile dateCol = dateCols.get(0);
            String sig = "TIME:" + dateCol.name + ":" + primaryMetric.name;
            usedChartSignatures.add(sig);
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.AREA_CHART)
                    .title(primaryMetric.name + " Trend over Time")
                    .sheetName(sheetName)
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn(dateCol.name)
                            .valueColumn(primaryMetric.name)
                            .aggregation("SUM")
                            .build())
                    .build(), sheet);
        }

        ColumnProfile primaryCat = null;
        for (ColumnProfile cat : categoricalCols) {
            if (cat.distinctCount >= 2 && cat.distinctCount <= 30) {
                primaryCat = cat;
                break;
            }
        }
        if (primaryCat != null) {
            String valCol = primaryMetric != null ? primaryMetric.name : primaryCat.name;
            String agg = primaryMetric != null ? "SUM" : "COUNT";
            String sig = "BAR:" + primaryCat.name + ":" + valCol;
            if (usedChartSignatures.add(sig)) {
                addWidgetQuietly(dashboard, DashboardWidget.builder()
                        .type(WidgetType.BAR_CHART)
                        .title(valCol + " by " + primaryCat.name)
                        .sheetName(sheetName)
                        .chartConfig(ChartConfig.builder()
                                .categoryColumn(primaryCat.name)
                                .valueColumn(valCol)
                                .aggregation(agg)
                                .mergeSimilarCategories(true)
                                .build())
                        .build(), sheet);
            }
        }

        ColumnProfile lowCardCat = null;
        for (ColumnProfile cat : categoricalCols) {
            if (cat != primaryCat && cat.distinctCount >= 2 && cat.distinctCount <= 8) {
                lowCardCat = cat;
                break;
            }
        }
        if (lowCardCat != null) {
            String valCol = primaryMetric != null ? primaryMetric.name : lowCardCat.name;
            String agg = primaryMetric != null ? "SUM" : "COUNT";
            String sig = "DONUT:" + lowCardCat.name + ":" + valCol;
            if (usedChartSignatures.add(sig)) {
                addWidgetQuietly(dashboard, DashboardWidget.builder()
                        .type(WidgetType.DONUT_CHART)
                        .title(valCol + " Distribution by " + lowCardCat.name)
                        .sheetName(sheetName)
                        .chartConfig(ChartConfig.builder()
                                .categoryColumn(lowCardCat.name)
                                .valueColumn(valCol)
                                .aggregation(agg)
                                .mergeSimilarCategories(true)
                                .build())
                        .build(), sheet);
            }
        }

        ColumnProfile secondaryCat = null;
        for (ColumnProfile cat : categoricalCols) {
            if (cat != primaryCat && cat != lowCardCat && cat.distinctCount >= 2 && cat.distinctCount <= 40) {
                secondaryCat = cat;
                break;
            }
        }
        if (secondaryCat != null) {
            String valCol = secondaryMetric != null ? secondaryMetric.name : (primaryMetric != null ? primaryMetric.name : secondaryCat.name);
            String agg = (secondaryMetric != null || primaryMetric != null) ? "SUM" : "COUNT";
            String sig = "BAR2:" + secondaryCat.name + ":" + valCol;
            if (usedChartSignatures.add(sig)) {
                addWidgetQuietly(dashboard, DashboardWidget.builder()
                        .type(WidgetType.BAR_CHART)
                        .title("Top " + secondaryCat.name + " by " + valCol)
                        .sheetName(sheetName)
                        .chartConfig(ChartConfig.builder()
                                .categoryColumn(secondaryCat.name)
                                .valueColumn(valCol)
                                .aggregation(agg)
                                .mergeSimilarCategories(true)
                                .build())
                        .build(), sheet);
            }
        }

        List<String> tableCols = new ArrayList<>();
        if (primaryCat != null) tableCols.add(primaryCat.name);
        if (!dateCols.isEmpty() && !tableCols.contains(dateCols.get(0).name)) tableCols.add(dateCols.get(0).name);
        if (secondaryCat != null && !tableCols.contains(secondaryCat.name)) tableCols.add(secondaryCat.name);
        if (primaryMetric != null && !tableCols.contains(primaryMetric.name)) tableCols.add(primaryMetric.name);
        if (secondaryMetric != null && !tableCols.contains(secondaryMetric.name)) tableCols.add(secondaryMetric.name);

        for (String h : sheet.getHeaders()) {
            if (tableCols.size() >= 5) break;
            if (!tableCols.contains(h)) tableCols.add(h);
        }

        if (!tableCols.isEmpty()) {
            addWidgetQuietly(dashboard, DashboardWidget.builder()
                    .type(WidgetType.TABLE)
                    .title("Records Explorer (" + sheetName + ")")
                    .sheetName(sheetName)
                    .columns(tableCols)
                    .build(), sheet);
        }

        log.info("Auto-generated dashboard '{}' with {} widgets for userId={}", finalName, dashboard.getWidgets().size(), userId);
        return dashboard;
    }

    @Override
    public Dashboard autoGenerateDashboard(String name, String sheetName, ExcelWorkbookData workbook) {
        String fileName = workbook != null ? workbook.getFileName() : null;
        return autoGenerateDashboard(0L, null, fileName, name, sheetName, workbook);
    }

    private static class ColumnProfile {
        String name;
        boolean isNumeric;
        boolean isDate;
        boolean isCategorical;
        boolean isIdLike;
        long distinctCount;
        int metricScore;
        int dimensionScore;
    }

    private List<ColumnProfile> profileSheetColumns(ExcelSheetData sheet) {
        List<ColumnProfile> list = new ArrayList<>();
        List<String> headers = sheet.getHeaders() != null ? sheet.getHeaders() : Collections.emptyList();
        List<Map<String, Object>> rows = sheet.getRows() != null ? sheet.getRows() : Collections.emptyList();

        int sampleSize = Math.min(rows.size(), 200);

        for (String col : headers) {
            ColumnProfile cp = new ColumnProfile();
            cp.name = col;
            String lower = col.toLowerCase().trim();

            Set<Object> distinctSample = new HashSet<>();
            int numericCount = 0;
            int dateCount = 0;
            int nonNullCount = 0;

            for (int r = 0; r < sampleSize; r++) {
                Object v = rows.get(r).get(col);
                if (v != null) {
                    nonNullCount++;
                    distinctSample.add(v);
                    if (isQuickNumeric(v)) {
                        numericCount++;
                    } else if (isQuickDate(v)) {
                        dateCount++;
                    }
                }
            }

            cp.distinctCount = distinctSample.size();

            cp.isIdLike = ID_COLUMN_PATTERN.matcher(lower).matches()
                    || (cp.distinctCount == sampleSize && sampleSize > 10 && lower.contains("id"));

            double numericRatio = nonNullCount == 0 ? 0.0 : (double) numericCount / nonNullCount;
            boolean nameIndicatesDate = lower.contains("date") || lower.contains("time") || lower.contains("year")
                    || lower.contains("month") || lower.contains("created") || lower.contains("timestamp")
                    || lower.contains("period") || lower.contains("quarter");
            double dateRatio = nonNullCount == 0 ? 0.0 : (double) dateCount / nonNullCount;

            if (numericRatio >= 0.75 && !nameIndicatesDate) {
                cp.isNumeric = true;
                int score = 10;
                if (METRIC_NAME_STRONG.matcher(lower).matches()) {
                    score += 60;
                } else if (METRIC_NAME_MODERATE.matcher(lower).matches()) {
                    score += 30;
                }
                if (cp.isIdLike) {
                    score -= 50;
                }
                cp.metricScore = score;
            } else if (dateRatio >= 0.6 || (nameIndicatesDate && dateRatio >= 0.2)) {
                cp.isDate = true;
            } else {
                cp.isCategorical = true;
                int score = 10;
                if (DIMENSION_NAME.matcher(lower).matches()) {
                    score += 50;
                }
                if (cp.distinctCount >= 2 && cp.distinctCount <= 12) {
                    score += 35;
                } else if (cp.distinctCount > 12 && cp.distinctCount <= 30) {
                    score += 15;
                } else if (cp.distinctCount > 40) {
                    score -= 20;
                }
                if (cp.isIdLike) {
                    score -= 40;
                }
                cp.dimensionScore = score;
            }

            list.add(cp);
        }
        return list;
    }

    private static boolean isQuickNumeric(Object val) {
        if (val == null) return false;
        if (val instanceof Number) return true;
        String s = val.toString().trim();
        if (s.isEmpty()) return false;
        int len = s.length();
        int i = 0;
        char first = s.charAt(0);
        if (first == '+' || first == '-') i++;
        if (i >= len) return false;
        boolean hasDot = false;
        boolean hasDigit = false;
        for (; i < len; i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            } else if (c == '.' && !hasDot) {
                hasDot = true;
            } else if (c == ',') {
                continue;
            } else {
                return false;
            }
        }
        return hasDigit;
    }

    private static Double parseQuickDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        if (!isQuickNumeric(val)) return null;
        try {
            return Double.parseDouble(val.toString().trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isQuickDate(Object val) {
        if (val == null) return false;
        if (val instanceof java.util.Date || val instanceof TemporalAccessor) return true;
        String s = val.toString().trim();
        if (s.length() < 6) return false;
        char c0 = s.charAt(0);
        if (c0 < '0' || c0 > '9') {
            if (!Character.isLetter(c0)) return false;
        }
        return ISO_DATE_PATTERN.matcher(s).matches() || ALT_DATE_PATTERN.matcher(s).matches();
    }

    private void addWidgetQuietly(Dashboard dashboard, DashboardWidget widget, ExcelSheetData sheet) {
        try {
            widget.setWidgetId(UUID.randomUUID().toString());
            validateWidgetConfig(widget, sheet);
            computeWidgetData(widget, sheet);
            dashboard.addWidget(widget);
        } catch (Exception e) {
            log.debug("Skipping candidate widget '{}' ({}): {}", widget.getTitle(), widget.getType(), e.getMessage());
        }
    }

    private String formatKpiTitle(String prefix, String columnName) {
        String clean = columnName.replace('_', ' ');
        if (clean.toLowerCase().startsWith(prefix.toLowerCase())) {
            return clean;
        }
        return prefix + " " + clean;
    }

    private String determineDefaultAggregation(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return "SUM";
        }
        String clean = columnName.trim().toLowerCase();
        if (AVERAGE_METRIC_PATTERN.matcher(clean).matches()) {
            return "AVERAGE";
        }
        if (SUM_METRIC_PATTERN.matcher(clean).matches()) {
            return "SUM";
        }
        return "SUM";
    }
}
