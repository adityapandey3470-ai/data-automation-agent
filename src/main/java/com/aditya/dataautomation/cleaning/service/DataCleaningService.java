package com.aditya.dataautomation.cleaning.service;

import com.aditya.dataautomation.cleaning.model.*;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;

import java.util.List;

public interface DataCleaningService {

    CleaningResult execute(ExcelWorkbookData workbook, TransformationRequest request);
    List<CleaningResult> executePipeline(ExcelWorkbookData workbook, List<TransformationRequest> requests);
}
