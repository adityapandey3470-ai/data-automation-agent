package com.aditya.dataautomation.model.excel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExcelWorkbookData {

    private final String fileName;
    private final List<ExcelSheetData> sheets;


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
