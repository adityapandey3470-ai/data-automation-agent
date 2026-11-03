package com.aditya.dataautomation.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveProjectRequest {

    private String projectId;

    @NotBlank(message = "Project name is required")
    private String name;

    private String description;

    private String activeSheetName;
}
