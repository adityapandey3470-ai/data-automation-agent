package com.aditya.dataautomation.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSummaryDto {

    private String id;
    private String name;
    private String description;
    private String fileName;
    private Long fileSizeBytes;
    private Integer rowCount;
    private Integer columnCount;
    private Integer sheetCount;
    private String activeSheetName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
