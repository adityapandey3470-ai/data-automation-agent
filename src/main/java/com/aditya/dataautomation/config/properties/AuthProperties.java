package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /**
     * Whether to expose password reset tokens in HTTP response bodies (only for non-prod test harness).
     */
    private boolean exposeDevToken = false;

    @NotBlank(message = "Frontend URL must not be blank")
    private String frontendUrl = "http://localhost:5173";
}
