package com.aditya.dataautomation.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a chart widget (BAR_CHART, LINE_CHART, PIE_CHART).
 * Specifies the category axis, value axis, and aggregation to apply.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChartConfig {

    /** Column used as the category/group axis (e.g. "Region") */
    private String categoryColumn;

    /** Column used as the value axis (e.g. "Revenue") */
    private String valueColumn;

    /** Aggregation to apply per category group */
    private String aggregation;

    /** Optional flag to merge similar categories ignoring casing and leading/trailing whitespace */
    private Boolean mergeSimilarCategories;

    /** Computed chart data points — populated by the service */
    private List<ChartDataPoint> dataPoints;

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChartDataPoint {
        private final Object label;
        private final Object value;
    }
}
