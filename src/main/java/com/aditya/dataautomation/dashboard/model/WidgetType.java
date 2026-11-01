package com.aditya.dataautomation.dashboard.model;

/**
 * Types of dashboard widgets.
 */
public enum WidgetType {

    /** Single numeric KPI value (COUNT, SUM, AVERAGE, MIN, MAX) */
    KPI,

    /** Tabular data with selected columns */
    TABLE,

    /** Bar chart — category column vs aggregated value column */
    BAR_CHART,

    /** Line chart — category column vs aggregated value column */
    LINE_CHART,

    /** Pie chart — category column vs aggregated value column */
    PIE_CHART,

    /** Area chart — category/date column vs aggregated value column */
    AREA_CHART,

    /** Donut chart — category column vs aggregated value column with hollow center */
    DONUT_CHART
}
