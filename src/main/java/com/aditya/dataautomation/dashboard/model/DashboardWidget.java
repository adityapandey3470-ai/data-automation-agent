package com.aditya.dataautomation.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * A single widget on a dashboard.
 * Configuration fields are set by the user; computed data is populated by the service.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardWidget {

    /** Unique widget identifier */
    private String widgetId;

    /** Widget type */
    private WidgetType type;

    /** Human-readable title */
    private String title;

    /** Sheet this widget draws data from */
    private String sheetName;

    /** Column for KPI computation */
    private String columnName;

    /** Aggregation for KPI: COUNT, SUM, AVERAGE, MIN, MAX */
    private String aggregation;

    /** Computed KPI value */
    private Object kpiValue;

    /** Period-over-period percentage growth (e.g. +14.2%) */
    private Double growthRate;

    /** Growth trend direction: "UP", "DOWN", "FLAT" */
    private String growthTrend;

    /** Target or benchmark value for threshold monitoring */
    private Double targetValue;

    /** Operational threshold alert status: "SUCCESS", "WARNING", "NORMAL" */
    private String alertStatus;

    /** Columns to include in the table widget */
    private List<String> columns;

    /** Computed table rows */
    private List<Map<String, Object>> tableData;

    /** Chart config for BAR_CHART, LINE_CHART, PIE_CHART */
    private ChartConfig chartConfig;
}
