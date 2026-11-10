package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Data
@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    @NotEmpty(message = "Allowed CORS origins list must not be empty")
    private List<String> allowedOrigins = List.of(
            "http://localhost:5173"
    );
}
