package com.aditya.dataautomation.auth.controller;

import com.aditya.dataautomation.auth.dto.AuthResponse;
import com.aditya.dataautomation.auth.dto.LoginRequest;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.dto.UserDto;
import com.aditya.dataautomation.auth.security.UserPrincipal;
import com.aditya.dataautomation.auth.service.AuthService;
import com.aditya.dataautomation.auth.service.RefreshTokenService;
import com.aditya.dataautomation.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and session management")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.auth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie-same-site:Strict}")
    private String cookieSameSite;

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(RefreshTokenService.COOKIE_NAME, value != null ? value : "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        Object attr = request.getAttribute("clientIp");
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    @PostMapping("/signup")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        try {
            AuthResponse response = authService.signup(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // concurrent signup with the same email: answer exactly like the "already registered" path
            return ResponseEntity.status(HttpStatus.CREATED).body(authService.genericSignupResponse(request.getEmail()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate an existing user")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        AuthResponse response = authService.login(request, clientIp);
        if (response.getUser() != null) {
            RefreshTokenService.Issued issued = refreshTokenService.issueForUser(response.getUser().getId());
            ResponseCookie cookie = refreshCookie(issued.rawToken(), issued.maxAgeSeconds());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and issue new access token")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = RefreshTokenService.COOKIE_NAME, required = false) String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new com.aditya.dataautomation.exception.AuthenticationFailedException("Refresh token is missing");
        }
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        AuthResponse response = authService.buildSessionResponse(rotation.user());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (rotation.newRawToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE,
                    refreshCookie(rotation.newRawToken(), rotation.maxAgeSeconds()).toString());
        }
        return builder.body(response);
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

    @PostMapping("/verify-email")
    @Operation(summary = "Verify user email address using single-use verification token")
    public ResponseEntity<AuthResponse> verifyEmail(
            @Valid @RequestBody com.aditya.dataautomation.auth.dto.VerifyEmailRequest request) {
        AuthResponse response = authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend email verification link")
    public ResponseEntity<java.util.Map<String, Object>> resendVerification(
            @Valid @RequestBody com.aditya.dataautomation.auth.dto.ResendVerificationRequest request) {
        AuthResponse response = authService.resendVerification(request.getEmail());
        return ResponseEntity.ok(java.util.Map.of(
                "success", true,
                "message", response.getMessage() != null ? response.getMessage() : "Verification link sent.",
                "smtpConfigured", response.isSmtpConfigured()
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

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate current session token")
    public ResponseEntity<java.util.Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = RefreshTokenService.COOKIE_NAME, required = false) String rawRefreshToken) {
        authService.logout(authHeader);
        refreshTokenService.revokeByRawToken(rawRefreshToken);
        ResponseCookie cookie = refreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(java.util.Map.of(
                        "success", true,
                        "message", "Logged out successfully"
                ));
    }
}
