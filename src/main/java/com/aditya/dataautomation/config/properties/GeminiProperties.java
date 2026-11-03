package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.ai.gemini")
public class GeminiProperties {

    private String apiKey;

    private String model = "gemini-2.5-flash";

    @Min(value = 1, message = "Timeout seconds must be at least 1")
    private int timeoutSeconds = 15;
}
