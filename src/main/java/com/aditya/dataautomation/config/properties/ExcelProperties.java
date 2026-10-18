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
}
