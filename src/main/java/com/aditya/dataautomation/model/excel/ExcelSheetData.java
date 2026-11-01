package com.aditya.dataautomation.model.excel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ExcelSheetData {

    private final String sheetName;
    private final List<String> headers;
    private final List<Map<String, Object>> rows;

    @JsonCreator
    public ExcelSheetData(
            @JsonProperty("sheetName") String sheetName,
            @JsonProperty("headers") List<String> headers,
            @JsonProperty("rows") List<Map<String, Object>> rows) {
        this.sheetName = sheetName;
        this.headers = headers;
        this.rows = rows;
    }

    public int getRowCount() {
        return rows != null ? rows.size() : 0;
    }

    public int getColumnCount() {
        return headers != null ? headers.size() : 0;
    }


    public boolean hasColumn(String columnName) {
        if (columnName == null || headers == null) {
            return false;
        }
        return headers.stream().anyMatch(h -> h.equalsIgnoreCase(columnName));
    }

    public String resolveColumnName(String columnName) {
        if (columnName == null || headers == null) {
            return null;
        }
        return headers.stream()
                .filter(h -> h.equalsIgnoreCase(columnName))
                .findFirst()
                .orElse(null);
    }
}
