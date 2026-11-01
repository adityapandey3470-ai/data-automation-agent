package com.aditya.dataautomation.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForgotPasswordResponse {

    private String message;
    private boolean smtpConfigured;
    private String emailDeliveryStatus;
    private String smtpInstructions;
    private String devResetToken;
}
