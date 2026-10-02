package com.aditya.dataautomation.model.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class ExcelSheetData {

    private final String sheetName;
    private final List<String> headers;
    private final List<Map<String, Object>> rows;

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
