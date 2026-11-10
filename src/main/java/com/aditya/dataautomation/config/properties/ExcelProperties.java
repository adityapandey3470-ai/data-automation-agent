package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.excel")
public class ExcelProperties {

    @Min(value = 1, message = "Preview rows limit must be at least 1")
    private int previewRows = 10;

    @Min(value = 100, message = "Max rows per sheet must be at least 100")
    private int maxRows = 100000;

    @Min(value = 10, message = "Max columns per sheet must be at least 10")
    private int maxColumns = 200;

    @Min(value = 1, message = "Max sheets must be at least 1")
    private int maxSheets = 50;

    @Min(value = 1048576, message = "Max uncompressed bytes must be at least 1MB")
    private long maxUncompressedBytes = 419430400L;

    @Min(value = 1, message = "Max concurrent parses must be at least 1")
    private int maxConcurrentParses = 2;

    /** Upper bound on rows x columns across the whole workbook (memory guard). */
    private long maxCells = 5_000_000L;
}
