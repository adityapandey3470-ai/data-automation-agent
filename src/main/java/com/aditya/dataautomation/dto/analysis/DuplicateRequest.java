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
public class DuplicateRequest {

    @NotBlank(message = "sheetName is required")
    private String sheetName;


    private List<String> columns;
}
