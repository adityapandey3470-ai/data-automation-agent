package com.aditya.dataautomation.analysis.service;

import com.aditya.dataautomation.analysis.model.*;
import com.aditya.dataautomation.model.excel.ExcelSheetData;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface DataAnalysisService {



    int countRows(ExcelSheetData sheet);

    long countNonEmpty(ExcelSheetData sheet, String column);

    long countNulls(ExcelSheetData sheet, String column);

    Set<Object> distinctValues(ExcelSheetData sheet, String column);



    Object sum(ExcelSheetData sheet, String column);

    Object average(ExcelSheetData sheet, String column);

    Object min(ExcelSheetData sheet, String column);

    Object max(ExcelSheetData sheet, String column);



    List<Map<String, Object>> filter(ExcelSheetData sheet, List<FilterCondition> conditions);



    List<Map<String, Object>> sort(ExcelSheetData sheet, List<SortCondition> sortConditions);



    GroupResult groupBy(ExcelSheetData sheet, String groupByColumn,
                        String aggregationColumn, AggregationFunction aggregation);



    List<Map<String, Object>> findDuplicateRows(ExcelSheetData sheet);

    List<Map<String, Object>> findDuplicatesByColumns(ExcelSheetData sheet, List<String> columns);



    ColumnStatistics getColumnStatistics(ExcelSheetData sheet, String column);
}
