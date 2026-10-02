package com.aditya.dataautomation.dto.analysis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SortRequest {

    @NotBlank(message = "sheetName is required")
    private String sheetName;

    @NotEmpty(message = "At least one sort condition is required")
    private List<SortConditionDto> sortBy;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortConditionDto {

        @NotBlank(message = "column is required")
        private String column;

        @NotBlank(message = "direction is required (ASC or DESC)")
        private String direction;
    }
}
