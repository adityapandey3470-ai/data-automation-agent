package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT secret must not be blank")
    @Size(min = 32, message = "JWT secret must be at least 32 characters for HMAC-SHA256 security")
    private String secret;

    @Min(value = 1, message = "JWT expiration hours must be at least 1")
    private int expirationHours = 72;
}
