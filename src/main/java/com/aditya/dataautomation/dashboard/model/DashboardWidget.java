package com.aditya.dataautomation.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardWidget {

    private String widgetId;

    private WidgetType type;

    private String title;

    private String sheetName;

    private String columnName;

    private String aggregation;

    private Object kpiValue;

    private Double growthRate;

    private String growthTrend;

    private Double targetValue;

    private String alertStatus;

    private List<String> columns;

    private List<Map<String, Object>> tableData;

    private ChartConfig chartConfig;
}
