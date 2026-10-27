package com.aditya.dataautomation.export.service;

import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;

/**
 * Exports processed workbook data to Excel (.xlsx) or CSV format.
 */
public interface DataExportService {

    /**
     * Export the entire workbook to .xlsx bytes.
     */
    byte[] exportToExcel(ExcelWorkbookData workbook);

    /**
     * Export a single sheet to .xlsx bytes.
     */
    byte[] exportSheetToExcel(ExcelSheetData sheet);

    /**
     * Export a single sheet to CSV bytes (UTF-8, comma-separated).
     */
    byte[] exportSheetToCsv(ExcelSheetData sheet);
}
