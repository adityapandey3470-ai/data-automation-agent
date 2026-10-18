package com.aditya.dataautomation.excel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkbookSummaryDto {

    private final String fileId;
    private final String fileName;
    private final long fileSize;
    private final int sheetCount;
    private final int totalRows;
    private final boolean active;
    private final long uploadedAt;
    private final List<String> sheetNames;
}
