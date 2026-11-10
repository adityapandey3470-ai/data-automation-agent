package com.aditya.dataautomation.dashboard.service.impl;

import com.aditya.dataautomation.common.util.NumericParser;
import com.aditya.dataautomation.dashboard.entity.DashboardCollaboratorEntity;
import com.aditya.dataautomation.dashboard.entity.DashboardEntity;
import com.aditya.dataautomation.dashboard.exception.DashboardException;
import com.aditya.dataautomation.dashboard.model.*;
import com.aditya.dataautomation.dashboard.repository.DashboardCollaboratorRepository;
import com.aditya.dataautomation.dashboard.repository.DashboardRepository;
import com.aditya.dataautomation.dashboard.service.DashboardService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private static final Pattern METRIC_NAME_STRONG = Pattern.compile(".*(revenue|sales|profit|amount|amt|total|cost|price|income|budget|spend|exp|expense|balance|bal|turnover|rev|kamai|bikri|munafa|kharcha|rakam|laabh|kosh).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern METRIC_NAME_MODERATE = Pattern.compile(".*(quantity|qty|units|score|rating|discount|rate|weight|hours|count|target|val|value|volume|vol|dar|shulk).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIMENSION_NAME = Pattern.compile(".*(category|region|segment|status|country|city|state|product|department|brand|customer|channel|gender|type|tier|shreni|vibhag|sthan|graahak).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AVERAGE_METRIC_PATTERN = Pattern.compile(".*(price|rate|unitprice|average|avg|score|rating|percentage|percent|dar|ausat).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUM_METRIC_PATTERN = Pattern.compile(".*(amount|amt|total|quantity|qty|units|revenue|rev|sales|cost|count|sum|exp|expense|turnover|kamai|bikri|munafa|kharcha|rakam).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_SENTIMENT_PATTERN = Pattern.compile(".*(cost|expense|spend|refund|return|churn|loss|defect|error|delay|latency|incident|waste|bounce|penalty|fine|damage|bad|cancel|dispute|tax|debt|liability|kharcha|nuksan).*", Pattern.CASE_INSENSITIVE);

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
    private static final int MAX_DASHBOARDS_PER_USER = 100;
    private static final int MAX_WIDGETS_JSON_BYTES = 5 * 1024 * 1024;
    private final Map<String, Dashboard> dashboards = new ConcurrentHashMap<>();

    private final DashboardRepository dashboardRepository;
    private final DashboardCollaboratorRepository collaboratorRepository;
    private final ObjectMapper objectMapper;

    public DashboardServiceImpl() {
        this(null, null, new ObjectMapper());
    }

    @Autowired
    public DashboardServiceImpl(DashboardRepository dashboardRepository,
                                DashboardCollaboratorRepository collaboratorRepository,
                                ObjectMapper objectMapper) {
        this.dashboardRepository = dashboardRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.objectMapper = objectMapper;
    }

    private void lockDashboardRow(String dashboardId) {
        if (dashboardRepository != null && dashboardId != null && !dashboardId.isBlank()) {
            dashboardRepository.findByDashboardIdForUpdate(dashboardId);
        }
    }

    private void persistDashboard(Dashboard dashboard) {
        if (dashboard == null || dashboard.getDashboardId() == null || dashboardRepository == null) return;
        try {
            String json = objectMapper.writeValueAsString(dashboard.getWidgets());
            if (json.getBytes(StandardCharsets.UTF_8).length > MAX_WIDGETS_JSON_BYTES) {
                throw new DashboardException("Dashboard size exceeds maximum limit of 5MB");
            }
            DashboardEntity entity = DashboardEntity.builder()
                    .dashboardId(dashboard.getDashboardId())
                    .userId(dashboard.getUserId())
                    .fileId(dashboard.getFileId())
                    .fileName(dashboard.getFileName())
                    .name(dashboard.getName())
                    .sheetName(dashboard.getSheetName())
                    .widgetsJson(json)
                    .createdAt(dashboard.getCreatedAt() != null ? dashboard.getCreatedAt() : LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            dashboardRepository.save(entity);
        } catch (DashboardException de) {
            throw de;
        } catch (Exception e) {
            log.error("Failed to persist dashboard to DB: {}", e.getMessage(), e);
            throw new DashboardException("Failed to save dashboard to persistent storage: " + e.getMessage());
        }
    }

    private Dashboard toDashboard(DashboardEntity entity) {
        if (entity == null) return null;
        List<DashboardWidget> widgets = new CopyOnWriteArrayList<>();
        if (entity.getWidgetsJson() != null && !entity.getWidgetsJson().isBlank() && objectMapper != null) {
            try {
                List<DashboardWidget> parsed = objectMapper.readValue(entity.getWidgetsJson(), new TypeReference<List<DashboardWidget>>() {});
                widgets.addAll(parsed);
            } catch (Exception e) {
                log.warn("Failed to deserialize widgets for dashboard {}: {}", entity.getDashboardId(), e.getMessage());
            }
        }
        Dashboard d = Dashboard.builder()
                .dashboardId(entity.getDashboardId())
                .userId(entity.getUserId())
                .fileId(entity.getFileId())
                .fileName(entity.getFileName())
                .name(entity.getName())
                .sheetName(entity.getSheetName())
                .createdAt(entity.getCreatedAt())
                .widgets(widgets)
                .build();
        return d;
    }

    private Dashboard loadFromDb(String dashboardId) {
        return dashboardRepository.findByDashboardId(dashboardId)
                .map(this::toDashboard)
                .orElse(null);
    }

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
        if (dashboardRepository != null && userId != null && dashboardRepository.countByUserId(userId) >= MAX_DASHBOARDS_PER_USER) {
            throw new DashboardException("Dashboard limit reached (" + MAX_DASHBOARDS_PER_USER + "). Please delete an old dashboard first.");
        }
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
        persistDashboard(dashboard);
        log.info("Created dashboard '{}' (id={}) for userId={}, fileId={}", name, dashboard.getDashboardId(), userId, fileId);
        return dashboard;
    }

    @Override
    public Dashboard createDashboard(String name, String sheetName, ExcelWorkbookData workbook) {
        String fileName = workbook != null ? workbook.getFileName() : null;
        return createDashboard(0L, null, fileName, name, sheetName, workbook);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public DashboardWidget addWidget(Long userId, String dashboardId, DashboardWidget widget, ExcelWorkbookData workbook) {
        validateWorkbook(workbook);
        lockDashboardRow(dashboardId);
        Dashboard dashboard = getDashboardWithPermission(userId, dashboardId, DashboardPermission.CUSTOMIZE);

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
        persistDashboard(dashboard);
        log.info("Added {} widget '{}' to dashboard '{}' by userId={}", widget.getType(), widget.getTitle(), dashboardId, userId);
        return widget;
    }

    @Override
    public DashboardWidget addWidget(String dashboardId, DashboardWidget widget, ExcelWorkbookData workbook) {
        return addWidget(null, dashboardId, widget, workbook);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void removeWidget(Long userId, String dashboardId, String widgetId) {
        lockDashboardRow(dashboardId);
        Dashboard dashboard = getDashboardWithPermission(userId, dashboardId, DashboardPermission.CUSTOMIZE);
        boolean removed = dashboard.removeWidget(widgetId);
        if (!removed) {
            log.warn("Widget '{}' not found or already removed from dashboard '{}' by userId={}", widgetId, dashboardId, userId);
            throw new DashboardException("Widget not found: " + widgetId);
        }
        persistDashboard(dashboard);
        log.info("Removed widget '{}' from dashboard '{}' by userId={}", widgetId, dashboardId, userId);
    }

    @Override
    public void removeWidget(String dashboardId, String widgetId) {
        removeWidget(null, dashboardId, widgetId);
    }

    @Override
    public Dashboard getDashboard(Long userId, String dashboardId, ExcelWorkbookData workbook) {
        validateWorkbook(workbook);
        Dashboard dashboard = getDashboardWithPermission(userId, dashboardId, DashboardPermission.VIEW);

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
        return getDashboardWithPermission(userId, dashboardId, DashboardPermission.VIEW);
    }

    @Override
    public Dashboard getDashboardWithPermission(Long userId, String dashboardId, DashboardPermission permission) {
        if (dashboardId == null || dashboardId.isBlank()) {
            throw new DashboardException("Dashboard ID must not be empty");
        }
        if (userId == null) {
            throw new DashboardException("Authentication required to access dashboard");
        }
        if (permission == null) {
            permission = DashboardPermission.VIEW;
        }

        // the database is the source of truth; the in-memory map is only used when no repository is wired (unit tests)
        Dashboard d = dashboardRepository != null ? loadFromDb(dashboardId) : dashboards.get(dashboardId);
        if (d == null) {
            throw new DashboardException("Dashboard not found: " + dashboardId);
        }

        if (Objects.equals(d.getUserId(), userId)) {
            return d;
        }

        if (permission == DashboardPermission.OWNER) {
            log.warn("Access denied: userId={} attempted OWNER action on dashboardId={} owned by userId={}",
                    userId, dashboardId, d.getUserId());
            throw new DashboardException("Access denied: only dashboard owner can perform this action");
        }

        Optional<DashboardCollaboratorEntity> collabOpt = collaboratorRepository.findByDashboardIdAndSharedWithUserId(dashboardId, userId);
        if (collabOpt.isEmpty()) {
            log.warn("Access denied: userId={} is not a collaborator on dashboardId={}", userId, dashboardId);
            throw new DashboardException("Dashboard not found or access denied");
        }

        DashboardCollaboratorEntity collab = collabOpt.get();
        if (permission == DashboardPermission.CUSTOMIZE && !collab.isCanCustomize()) {
            log.warn("Access denied: userId={} lacks CUSTOMIZE permission on dashboardId={}", userId, dashboardId);
            throw new DashboardException("Access denied: customize permission not granted");
        }

        return d;
    }

    @Override
    public List<Dashboard> listDashboards(Long userId) {
        if (userId == null) userId = 0L;
        final Long uid = userId;

        Map<String, Dashboard> merged = new LinkedHashMap<>();
        dashboardRepository.findByUserIdOrderByCreatedAtDesc(uid).forEach(entity -> {
            Dashboard d = toDashboard(entity);
            if (d != null) {
                merged.put(d.getDashboardId(), d);
            }
        });

        return merged.values().stream()
                .sorted(Comparator.comparing(Dashboard::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteDashboard(Long userId, String dashboardId) {
        Dashboard dashboard = getDashboardWithPermission(userId, dashboardId, DashboardPermission.OWNER);
        dashboards.remove(dashboard.getDashboardId());
        dashboardRepository.deleteById(dashboard.getDashboardId());
        collaboratorRepository.deleteByDashboardId(dashboard.getDashboardId());
        log.info("Deleted dashboard '{}' (id={}) by userId={}", dashboard.getName(), dashboardId, userId);
    }

    private Dashboard getDashboardAndVerifyOwnership(Long userId, String dashboardId) {
        return getDashboardWithPermission(userId, dashboardId, DashboardPermission.VIEW);
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

        // ---- Totals over ALL rows + PRIOR/RECENT windows for growth (O(n): dates parsed ONCE, no sorting) ----
        int totalRows = rows.size();
        String dateCol = findDateColumn(sheet);
        long[] ts = null;
        long minT = Long.MAX_VALUE;
        long maxT = Long.MIN_VALUE;
        if (dateCol != null && totalRows > 1) {
            ts = new long[totalRows];
            for (int i = 0; i < totalRows; i++) {
                Long t = parseDateToMillis(rows.get(i).get(dateCol));
                if (t == null) {
                    ts[i] = Long.MIN_VALUE;
                } else {
                    ts[i] = t;
                    if (t < minT) minT = t;
                    if (t > maxT) maxT = t;
                }
            }
            if (maxT <= minT) ts = null; // no usable dates -> fall back to row halves
        }

        final long dayMs = 86_400_000L;
        long recentStart = Long.MIN_VALUE;
        long priorStart = Long.MIN_VALUE;
        if (ts != null) {
            long spanDays = (maxT - minT) / dayMs;
            long windowDays = spanDays >= 60 ? 30 : (spanDays >= 14 ? 7 : Math.max(1, spanDays / 2));
            recentStart = maxT - windowDays * dayMs;       // recent = last N days
            priorStart = recentStart - windowDays * dayMs; // prior  = the N days before that
        }
        int mid = totalRows / 2;

        double sumAll = 0.0;
        long countAll = 0;
        long nonNullCells = 0;
        double minAll = Double.MAX_VALUE;
        double maxAll = -Double.MAX_VALUE;
        double sumPrior = 0.0;
        long countPrior = 0;
        double sumRecent = 0.0;
        long countRecent = 0;

        for (int i = 0; i < totalRows; i++) {
            Object raw = rows.get(i).get(column);
            if (raw == null || (raw instanceof String blank && blank.isBlank())) continue;
            nonNullCells++;

            double d;
            if (raw instanceof Number n) {
                d = n.doubleValue();
            } else {
                Double parsed = parseQuickDouble(raw);
                if (parsed == null) continue;
                d = parsed;
            }

            sumAll += d;
            countAll++;
            if (d < minAll) minAll = d;
            if (d > maxAll) maxAll = d;

            boolean inRecent;
            boolean inPrior;
            if (ts != null) {
                long t = ts[i];
                inRecent = t != Long.MIN_VALUE && t > recentStart;
                inPrior = t != Long.MIN_VALUE && t > priorStart && t <= recentStart;
            } else {
                inRecent = i >= mid;
                inPrior = i < mid;
            }
            if (inRecent) {
                sumRecent += d;
                countRecent++;
            } else if (inPrior) {
                sumPrior += d;
                countPrior++;
            }
        }

        if (countAll == 0) {
            widget.setKpiValue(BigDecimal.ZERO);
            widget.setAlertStatus("NORMAL");
            return;
        }

        double computedVal = switch (agg) {
            case "SUM" -> sumAll;
            case "AVERAGE" -> sumAll / countAll;
            case "MIN" -> minAll;
            case "MAX" -> maxAll;
            default -> sumAll;
        };

        BigDecimal kpiVal = BigDecimal.valueOf(computedVal).setScale(2, RoundingMode.HALF_UP);
        widget.setKpiValue(kpiVal);

        // fix 6: tell the user when many cells were ignored because they were not numeric
        if (nonNullCells > 0 && countAll < nonNullCells) {
            double coverage = 100.0 * countAll / nonNullCells;
            if (coverage < 95.0) {
                widget.setWarning(String.format(Locale.ROOT,
                        "Only %.0f%% of values in '%s' are numeric. %d non-numeric value(s) were ignored.",
                        coverage, widget.getColumnName(), nonNullCells - countAll));
            }
        }

        if (!"MIN".equals(agg) && !"MAX".equals(agg) && countPrior > 0 && countRecent > 0) {
            double priorAggVal = switch (agg) {
                case "SUM" -> sumPrior;
                case "AVERAGE" -> sumPrior / countPrior;
                default -> sumPrior;
            };

            double recentAggVal = switch (agg) {
                case "SUM" -> sumRecent;
                case "AVERAGE" -> sumRecent / countRecent;
                default -> sumRecent;
            };

            if (priorAggVal > 0) {
                double growth = ((recentAggVal - priorAggVal) / priorAggVal) * 100.0;
                setGrowthFields(widget, growth);
            }
        }

        if (widget.getGrowthRate() != null) {
            boolean isNegative = widget.getColumnName() != null && NEGATIVE_SENTIMENT_PATTERN.matcher(widget.getColumnName()).matches();
            if (widget.getGrowthRate() >= 5.0) {
                widget.setAlertStatus(isNegative ? "WARNING" : "SUCCESS");
            } else if (widget.getGrowthRate() <= -5.0) {
                widget.setAlertStatus(isNegative ? "SUCCESS" : "WARNING");
            } else {
                widget.setAlertStatus("NORMAL");
            }
        } else {
            widget.setAlertStatus("NORMAL");
        }
    }

    private String findDateColumn(ExcelSheetData sheet) {
        if (sheet == null || sheet.getHeaders() == null) return null;
        for (String h : sheet.getHeaders()) {
            String lower = h.toLowerCase(Locale.ROOT);
            if (lower.contains("date") || lower.contains("time") || lower.contains("created") || lower.contains("period") || lower.contains("timestamp")) {
                return sheet.resolveColumnName(h);
            }
        }
        List<Map<String, Object>> rows = sheet.getRows();
        if (rows != null && !rows.isEmpty()) {
            int checkLimit = Math.min(rows.size(), 20);
            for (String h : sheet.getHeaders()) {
                String resolved = sheet.resolveColumnName(h);
                int dateCount = 0;
                for (int i = 0; i < checkLimit; i++) {
                    if (isQuickDate(rows.get(i).get(resolved))) dateCount++;
                }
                if (dateCount >= Math.min(3, checkLimit)) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private void calculatePeriodGrowthForCount(DashboardWidget widget, ExcelSheetData sheet) {
        List<Map<String, Object>> rows = sheet.getRows();
        int totalRows = rows == null ? 0 : rows.size();
        if (totalRows < 2) {
            widget.setAlertStatus("NORMAL");
            return;
        }

        long prior = 0;
        long recent = 0;
        boolean usedDates = false;

        String dateCol = findDateColumn(sheet);
        if (dateCol != null) {
            long[] ts = new long[totalRows];
            long minT = Long.MAX_VALUE;
            long maxT = Long.MIN_VALUE;
            for (int i = 0; i < totalRows; i++) {
                Long t = parseDateToMillis(rows.get(i).get(dateCol));
                if (t == null) {
                    ts[i] = Long.MIN_VALUE;
                } else {
                    ts[i] = t;
                    if (t < minT) minT = t;
                    if (t > maxT) maxT = t;
                }
            }
            if (maxT > minT) {
                final long dayMs = 86_400_000L;
                long spanDays = (maxT - minT) / dayMs;
                long windowDays = spanDays >= 60 ? 30 : (spanDays >= 14 ? 7 : Math.max(1, spanDays / 2));
                long recentStart = maxT - windowDays * dayMs;
                long priorStart = recentStart - windowDays * dayMs;
                for (int i = 0; i < totalRows; i++) {
                    long t = ts[i];
                    if (t == Long.MIN_VALUE) continue;
                    if (t > recentStart) {
                        recent++;
                    } else if (t > priorStart) {
                        prior++;
                    }
                }
                usedDates = true;
            }
        }

        if (!usedDates) { // no usable date column -> compare first half vs second half of the rows
            int mid = totalRows / 2;
            prior = mid;
            recent = totalRows - mid;
        }

        if (prior > 0) {
            double growth = ((double) (recent - prior) / prior) * 100.0;
            setGrowthFields(widget, growth);
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

        // ---- Time-series bucketing (LINE/AREA on a date column): group by day/week/month/... BEFORE aggregating ----
        boolean timeChart = widget.getType() == WidgetType.LINE_CHART || widget.getType() == WidgetType.AREA_CHART;
        long[] catTime = null;
        String bucketMode = null;
        if (timeChart && !rows.isEmpty()) {
            String requested = config.getGranularity() == null
                    ? "AUTO" : config.getGranularity().trim().toUpperCase(Locale.ROOT);
            if (!"RAW".equals(requested)) {
                catTime = new long[rows.size()];
                long minT = Long.MAX_VALUE;
                long maxT = Long.MIN_VALUE;
                int valid = 0;
                int nonBlank = 0;
                for (int i = 0; i < catTime.length; i++) {
                    Object c = rows.get(i).get(catCol);
                    catTime[i] = Long.MIN_VALUE;
                    if (c == null || c.toString().isBlank()) continue;
                    nonBlank++;
                    if (c instanceof Number) continue; // plain numbers are not dates here
                    Long t = parseDateToMillis(c);
                    if (t == null) continue;
                    catTime[i] = t;
                    valid++;
                    if (t < minT) minT = t;
                    if (t > maxT) maxT = t;
                }
                if (nonBlank > 0 && valid >= 0.8 * nonBlank) {
                    bucketMode = requested;
                    if (!Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR").contains(bucketMode)) {
                        long spanDays = (maxT - minT) / 86_400_000L;
                        bucketMode = spanDays <= 50 ? "DAY" : spanDays <= 350 ? "WEEK" : spanDays <= 1500 ? "MONTH" : "YEAR";
                    }
                } else {
                    catTime = null; // not really a date column -> leave it as a normal category chart
                }
            }
        }

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Map<String, Object> row = rows.get(rowIdx);
            Object rawKey = row.get(catCol);
            String rawStr = (rawKey == null) ? "" : rawKey.toString().trim();
            if (bucketMode != null && catTime[rowIdx] != Long.MIN_VALUE) {
                rawStr = bucketLabel(catTime[rowIdx], bucketMode);
            }
            Object groupKey;
            if (rawStr.isEmpty()) {
                if (mergeSimilar) {
                    groupKey = "(blank)";
                    Map<String, Integer> counts = casingCounts.computeIfAbsent("(blank)", k -> new LinkedHashMap<>());
                    counts.put("(Blank)", counts.getOrDefault("(Blank)", 0) + 1);
                } else {
                    groupKey = "(Blank)";
                }
            } else if (mergeSimilar) {
                String norm = rawStr.toLowerCase(Locale.ROOT);
                groupKey = norm;
                Map<String, Integer> counts = casingCounts.computeIfAbsent(norm, k -> new LinkedHashMap<>());
                counts.put(rawStr, counts.getOrDefault(rawStr, 0) + 1);
            } else {
                groupKey = rawStr;
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

        Map<String, GroupAcc> labelToAcc = new LinkedHashMap<>();
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
            labelToAcc.put(String.valueOf(displayLabel), entry.getValue());
        }

        List<ChartConfig.ChartDataPoint> points = new ArrayList<>(labelToAcc.size());
        for (Map.Entry<String, GroupAcc> entry : labelToAcc.entrySet()) {
            points.add(ChartConfig.ChartDataPoint.builder()
                    .label(entry.getKey())
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

            GroupAcc remainderAcc = new GroupAcc();
            for (ChartConfig.ChartDataPoint p : remainder) {
                GroupAcc acc = labelToAcc.get(String.valueOf(p.getLabel()));
                if (acc != null) {
                    remainderAcc.sum += acc.sum;
                    remainderAcc.count += acc.count;
                    if (acc.min < remainderAcc.min) remainderAcc.min = acc.min;
                    if (acc.max > remainderAcc.max) remainderAcc.max = acc.max;
                }
            }

            if (remainderAcc.count > 0) {
                top10.add(ChartConfig.ChartDataPoint.builder()
                        .label("Other (" + remainder.size() + " categories)")
                        .value(remainderAcc.getResult(agg))
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

    /** First day of the day/week/month/quarter/year that contains the timestamp, as yyyy-MM-dd. */
    private static String bucketLabel(long epochMillis, String mode) {
        java.time.LocalDate d = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneOffset.UTC).toLocalDate();
        switch (mode) {
            case "WEEK" -> d = d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case "MONTH" -> d = d.withDayOfMonth(1);
            case "QUARTER" -> d = d.withMonth(((d.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
            case "YEAR" -> d = d.withDayOfYear(1);
            default -> { }
        }
        return d.toString();
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

        // FAST PATH: "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-ddTHH:mm:ss" without exceptions
        if (s.length() >= 10 && s.charAt(4) == '-' && s.charAt(7) == '-') {
            try {
                int year = Integer.parseInt(s, 0, 4, 10);
                int month = Integer.parseInt(s, 5, 7, 10);
                int day = Integer.parseInt(s, 8, 10, 10);
                if (s.length() == 10) {
                    return java.time.LocalDate.of(year, month, day)
                            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
                }
                if (s.length() == 19 && (s.charAt(10) == ' ' || s.charAt(10) == 'T')
                        && s.charAt(13) == ':' && s.charAt(16) == ':') {
                    int hh = Integer.parseInt(s, 11, 13, 10);
                    int mm = Integer.parseInt(s, 14, 16, 10);
                    int ss = Integer.parseInt(s, 17, 19, 10);
                    return java.time.LocalDateTime.of(year, month, day, hh, mm, ss)
                            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
                }
            } catch (RuntimeException ignored) {
                // not a plain ISO date -> fall through to the slower parsers below
            }
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
        Long firstTime = null;
        Long lastTime = null;
        for (ChartConfig.ChartDataPoint p : original) {
            Long t = parseDateToMillis(p.getLabel());
            if (t != null) {
                if (firstTime == null || t < firstTime) firstTime = t;
                if (lastTime == null || t > lastTime) lastTime = t;
            }
        }

        List<ChartConfig.ChartDataPoint> sampled = new ArrayList<>(targetBuckets);
        if (firstTime != null && lastTime != null && lastTime > firstTime) {
            long duration = lastTime - firstTime;
            long step = Math.max(1, duration / targetBuckets);
            int origIdx = 0;
            for (int b = 0; b < targetBuckets; b++) {
                long bucketStart = firstTime + b * step;
                long bucketEnd = (b == targetBuckets - 1) ? (lastTime + 1) : (bucketStart + step);
                List<ChartConfig.ChartDataPoint> chunk = new ArrayList<>();
                while (origIdx < original.size()) {
                    ChartConfig.ChartDataPoint p = original.get(origIdx);
                    Long t = parseDateToMillis(p.getLabel());
                    if (t != null && t >= bucketEnd && !chunk.isEmpty()) {
                        break;
                    }
                    chunk.add(p);
                    origIdx++;
                }
                if (chunk.isEmpty()) continue;
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
                sampled.add(ChartConfig.ChartDataPoint.builder().label(label).value(bucketVal).build());
            }
            if (!sampled.isEmpty()) return sampled;
        }

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

        int total = rows.size();
        int maxSamples = 1000;
        int step = Math.max(1, total / maxSamples);
        int numericCount = 0;
        int nonNullCount = 0;
        for (int i = 0; i < total && nonNullCount < maxSamples; i += step) {
            Object val = rows.get(i).get(resolved);
            if (val == null || (val instanceof String s && s.isBlank())) continue;
            nonNullCount++;
            if (NumericParser.isNumeric(val)) {
                numericCount++;
            }
        }

        if (nonNullCount == 0 || numericCount == 0) {
            throw new DashboardException("Column '" + column + "' does not contain numeric values. Cannot perform numeric aggregation.");
        }

        double ratio = (double) numericCount / nonNullCount;
        if (nonNullCount >= 5 && ratio < 0.50) {
            throw new DashboardException("Column '" + column + "' is only " + Math.round(ratio * 100)
                    + "% numeric. Clean or convert the column before aggregating it.");
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
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

        persistDashboard(dashboard);
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

        int totalRows = rows.size();
        int maxSamples = Math.min(totalRows, 300);
        int step = (totalRows <= maxSamples) ? 1 : Math.max(1, totalRows / maxSamples);

        for (String col : headers) {
            ColumnProfile cp = new ColumnProfile();
            cp.name = col;
            String lower = col.toLowerCase(Locale.ROOT).trim();

            Set<Object> distinctSample = new HashSet<>();
            int numericCount = 0;
            int dateCount = 0;
            int nonNullCount = 0;

            for (int r = 0; r < totalRows && nonNullCount < maxSamples; r += step) {
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
                    || (cp.distinctCount == nonNullCount && nonNullCount > 10 && lower.contains("id"));

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
        return NumericParser.isNumeric(val);
    }

    private static Double parseQuickDouble(Object val) {
        return NumericParser.parseDouble(val);
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
            log.warn("Skipping candidate widget '{}' ({}): {}", widget.getTitle(), widget.getType(), e.getMessage());
            String label = widget.getTitle() != null ? widget.getTitle() : String.valueOf(widget.getType());
            dashboard.addSkippedWidget(label + ": " + e.getMessage());
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
