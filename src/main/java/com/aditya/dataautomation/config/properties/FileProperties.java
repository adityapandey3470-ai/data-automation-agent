package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.file")
public class FileProperties {

    @Min(value = 1024, message = "Maximum file size must be at least 1024 bytes")
    private long maxSize = 10485760L;
}
