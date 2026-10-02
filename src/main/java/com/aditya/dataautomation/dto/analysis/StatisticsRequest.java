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
public class StatisticsRequest {

    @NotBlank(message = "sheetName is required")
    private String sheetName;

    @NotBlank(message = "column is required")
    private String column;
}
