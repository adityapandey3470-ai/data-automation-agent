package com.aditya.dataautomation.service;

import com.aditya.dataautomation.dto.excel.ExcelFileResponse;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import org.springframework.web.multipart.MultipartFile;

public interface ExcelFileService {


    ExcelFileResponse processExcelFile(MultipartFile file);

    ExcelWorkbookData parseWorkbook(MultipartFile file);
}
