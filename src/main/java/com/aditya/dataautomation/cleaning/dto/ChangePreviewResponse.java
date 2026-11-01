package com.aditya.dataautomation.cleaning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangePreviewResponse {

    private String operation;
    private String sheetName;
    private int rowsBefore;
    private int rowsAfter;
    private int rowsRemoved;
    private int cellsChanged;
    private int columnCountBefore;
    private int columnCountAfter;
    private List<String> headersBefore;
    private List<String> headersAfter;
    private List<Map<String, Object>> beforeRows;
    private List<Map<String, Object>> afterRows;
    private List<String> affectedColumns;
    private String summary;
}
