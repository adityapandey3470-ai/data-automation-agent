package com.aditya.dataautomation.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String tokenType;
    private UserDto user;
    private boolean emailVerificationRequired;
    private String message;
    private boolean smtpConfigured;
}
