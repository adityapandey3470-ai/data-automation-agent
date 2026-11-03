package com.aditya.dataautomation.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    private String email;

    private String password;

    private String name = "System Administrator";
}
