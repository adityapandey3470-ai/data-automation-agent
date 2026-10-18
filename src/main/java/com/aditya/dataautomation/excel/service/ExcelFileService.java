package com.aditya.dataautomation.excel.service;

import com.aditya.dataautomation.excel.dto.ExcelFileResponse;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import org.springframework.web.multipart.MultipartFile;

public interface ExcelFileService {

    ExcelFileResponse processExcelFile(MultipartFile file);

    ExcelWorkbookData parseWorkbook(MultipartFile file);
}
