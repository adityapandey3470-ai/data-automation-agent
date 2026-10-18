package com.aditya.dataautomation.excel.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExcelWorkbookData {

    private final String fileName;
    private final List<ExcelSheetData> sheets;

    @JsonCreator
    public ExcelWorkbookData(
            @JsonProperty("fileName") String fileName,
            @JsonProperty("sheets") List<ExcelSheetData> sheets) {
        this.fileName = fileName;
        this.sheets = sheets;
    }

    public ExcelSheetData getSheet(String sheetName) {
        if (sheetName == null || sheets == null) {
            return null;
        }
        return sheets.stream()
                .filter(s -> sheetName.equalsIgnoreCase(s.getSheetName()))
                .findFirst()
                .orElse(null);
    }
}
