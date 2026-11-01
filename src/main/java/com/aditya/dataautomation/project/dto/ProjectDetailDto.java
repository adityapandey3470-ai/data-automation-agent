package com.aditya.dataautomation.project.dto;

import com.aditya.dataautomation.dto.excel.ExcelFileResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailDto {

    private String id;
    private String name;
    private String description;
    private String fileName;
    private String activeSheetName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private ExcelFileResponse preview;
    private List<String> historyDescriptions;
}
