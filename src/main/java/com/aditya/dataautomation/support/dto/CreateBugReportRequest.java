package com.aditya.dataautomation.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBugReportRequest {

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 3000, message = "Description must be between 10 and 3000 characters")
    private String description;

    @NotBlank(message = "Steps to reproduce are required")
    @Size(min = 5, max = 3000, message = "Steps to reproduce must be between 5 and 3000 characters")
    private String stepsToReproduce;

    @NotBlank(message = "Severity level is required")
    private String severity;

    private String userEmail;
}
