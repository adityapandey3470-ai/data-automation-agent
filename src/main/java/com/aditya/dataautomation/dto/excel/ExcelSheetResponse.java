package com.aditya.dataautomation.dto.excel;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExcelSheetResponse {

    private final String sheetName;
    private final int rowCount;
    private final int columnCount;
    private final List<String> headers;
    private final List<Map<String, Object>> previewRows;
}
