package com.aditya.dataautomation.export.service;

import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;

public interface DataExportService {

    byte[] exportToExcel(ExcelWorkbookData workbook);

    byte[] exportSheetToExcel(ExcelSheetData sheet);

    byte[] exportSheetToCsv(ExcelSheetData sheet);
}
