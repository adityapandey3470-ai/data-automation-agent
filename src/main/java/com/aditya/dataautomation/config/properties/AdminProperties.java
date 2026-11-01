package com.aditya.dataautomation.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /**
     * Initial administrator email address. If blank, no admin user is auto-provisioned.
     */
    private String email;

    /**
     * Initial administrator password.
     */
    private String password;

    private String name = "System Administrator";
}
