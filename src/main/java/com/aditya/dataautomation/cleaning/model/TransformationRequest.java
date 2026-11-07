package com.aditya.dataautomation.cleaning.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransformationRequest {

    private String sheetName;
    private CleaningOperation operation;
    private String columnName;
    private List<String> columns;
    private Object value;
    private Object oldValue;
    private Object newValue;
    private TargetDataType targetType;
    private MissingValueStrategy missingValueStrategy;
    private DuplicateStrategy duplicateStrategy;
    private Boolean regex;
    private Boolean matchEntireCell;
    private Boolean caseInsensitive;
    private Boolean trimWhitespace;
    private Long baseVersion;
    private String dateOrder; // "DMY", "MDY" or null = auto-detect
}
