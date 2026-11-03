package com.aditya.dataautomation.config.properties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private String host;

    @Min(value = 1, message = "SMTP port must be valid")
    private int port = 587;

    private String username;

    private String password;

    @Email(message = "From address must be a valid email format")
    private String from = "noreply@tabufinery.io";
}
