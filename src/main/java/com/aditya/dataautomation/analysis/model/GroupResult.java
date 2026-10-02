package com.aditya.dataautomation.analysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;


@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupResult {

    private final String groupByColumn;
    private final String aggregationColumn;
    private final AggregationFunction aggregation;
    private final List<GroupEntry> groups;

    @Getter
    @Builder
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupEntry {

        private final Object key;
        private final long count;
        private final Object aggregationValue;
    }
}
