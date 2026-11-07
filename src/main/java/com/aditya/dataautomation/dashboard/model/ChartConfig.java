package com.aditya.dataautomation.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChartConfig {

    private String categoryColumn;

    private String valueColumn;

    private String aggregation;

    private Boolean mergeSimilarCategories;

    /** For LINE/AREA charts on a date column: AUTO (default), DAY, WEEK, MONTH, QUARTER, YEAR or RAW. */
    private String granularity;

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
