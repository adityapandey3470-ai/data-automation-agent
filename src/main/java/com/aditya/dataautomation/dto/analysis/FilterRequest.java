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
public class FilterRequest {

    @NotBlank(message = "sheetName is required")
    private String sheetName;

    @NotEmpty(message = "At least one filter condition is required")
    private List<FilterConditionDto> conditions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterConditionDto {

        @NotBlank(message = "column is required")
        private String column;

        @NotBlank(message = "operator is required")
        private String operator;

        private Object value;
    }
}
