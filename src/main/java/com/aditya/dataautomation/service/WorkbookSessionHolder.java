package com.aditya.dataautomation.service;

import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class WorkbookSessionHolder {

    private final AtomicReference<ExcelWorkbookData> currentWorkbook = new AtomicReference<>();

    public void store(ExcelWorkbookData workbook) {
        currentWorkbook.set(workbook);
    }

    public ExcelWorkbookData get() {
        return currentWorkbook.get();
    }

    public void clear() {
        currentWorkbook.set(null);
    }
}
