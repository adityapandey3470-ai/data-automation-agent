package com.aditya.dataautomation.auth.controller;

import com.aditya.dataautomation.auth.dto.AuthResponse;
import com.aditya.dataautomation.auth.dto.LoginRequest;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.dto.UserDto;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and session management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate an existing user")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Initiate password reset via email")
    public ResponseEntity<com.aditya.dataautomation.auth.dto.ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody com.aditya.dataautomation.auth.dto.ForgotPasswordRequest request) {
        com.aditya.dataautomation.auth.dto.ForgotPasswordResponse response = authService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using single-use reset token")
    public ResponseEntity<java.util.Map<String, Object>> resetPassword(
            @Valid @RequestBody com.aditya.dataautomation.auth.dto.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", "Password has been reset successfully. Please log in with your new password."
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(com.aditya.dataautomation.dto.ErrorResponse.builder()
                            .status(HttpStatus.UNAUTHORIZED.value())
                            .error("Unauthorized")
                            .message("Authentication required. Please sign in.")
                            .timestamp(java.time.LocalDateTime.now())
                            .build());
        }
        UserDto user = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(user);
    }
}
