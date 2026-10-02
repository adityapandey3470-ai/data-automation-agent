package com.aditya.dataautomation.dto.excel;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExcelFileResponse {

    private final String fileName;
    private final long fileSize;
    private final String contentType;
    private final int sheetCount;
    private final List<ExcelSheetResponse> sheets;
}
