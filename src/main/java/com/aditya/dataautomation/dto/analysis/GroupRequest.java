package com.aditya.dataautomation.dto.analysis;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GroupRequest {

    @NotBlank(message = "sheetName is required")
    private String sheetName;

    @NotBlank(message = "groupByColumn is required")
    private String groupByColumn;


    private String aggregationColumn;

    @NotBlank(message = "aggregation is required (COUNT, SUM, AVERAGE, MIN, MAX)")
    private String aggregation;
}
