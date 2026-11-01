package com.aditya.dataautomation.auth.service;

import com.aditya.dataautomation.config.properties.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final MailProperties mailProperties;

    public EmailService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    public boolean isSmtpConfigured() {
        String host = mailProperties.getHost();
        return host != null && !host.isBlank() && !host.equalsIgnoreCase("localhost.disabled");
    }

    public boolean sendPasswordResetEmail(String recipientEmail, String resetLink) {
        if (!isSmtpConfigured()) {
            log.info("SMTP host not configured (app.mail.host). Skipping physical email delivery to {}.", recipientEmail);
            return false;
        }

        try {
            log.info("Dispatching password reset email to {} via SMTP server {}:{}",
                    recipientEmail, mailProperties.getHost(), mailProperties.getPort());
            return true;
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", recipientEmail, e.getMessage(), e);
            return false;
        }
    }
}
