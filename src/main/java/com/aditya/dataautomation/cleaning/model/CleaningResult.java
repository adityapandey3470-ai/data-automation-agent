package com.aditya.dataautomation.cleaning.model;

import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CleaningResult {


    private final String operation;


    private final String affectedSheet;


    private final int rowsBefore;


    private final int rowsAfter;


    private final Integer rowsRemoved;


    private final Integer cellsChanged;


    private final Integer columnCount;


    private final String message;


    @JsonIgnore
    private final ExcelWorkbookData cleanedWorkbook;
}
