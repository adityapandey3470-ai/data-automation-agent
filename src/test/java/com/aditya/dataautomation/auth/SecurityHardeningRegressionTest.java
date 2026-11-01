package com.aditya.dataautomation.auth;

import com.aditya.dataautomation.admin.dto.AdminUserDto;
import com.aditya.dataautomation.admin.service.AdminService;
import com.aditya.dataautomation.auth.dto.ForgotPasswordRequest;
import com.aditya.dataautomation.auth.dto.ForgotPasswordResponse;
import com.aditya.dataautomation.auth.dto.ResetPasswordRequest;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.entity.PasswordResetToken;
import com.aditya.dataautomation.auth.repository.PasswordResetTokenRepository;
import com.aditya.dataautomation.auth.repository.UserRepository;
import com.aditya.dataautomation.auth.security.RateLimiterService;
import com.aditya.dataautomation.auth.service.AuthService;
import com.aditya.dataautomation.exception.InvalidRequestException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityHardeningRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    @DisplayName("Password reset token uses 256-bit SecureRandom URL-safe Base64 encoding with single-use enforcement")
    void testSecureRandomPasswordResetTokenLifecycle() {
        String testEmail = "securerandom_" + System.currentTimeMillis() + "@tabufinery.io";
        authService.signup(SignupRequest.builder()
                .name("Secure Random Tester")
                .email(testEmail)
                .password("StrongPassword123!")
                .build());

        ForgotPasswordResponse forgotRes = authService.requestPasswordReset(
                ForgotPasswordRequest.builder().email(testEmail).build());

        String devToken = forgotRes.getDevResetToken();
        assertThat(devToken).isNotNull();

        // 32 bytes Base64 URL-safe unpadded is exactly 43 characters
        assertThat(devToken).hasSize(43);
        // Validates URL-safe character set [A-Za-z0-9_-]
        assertThat(Pattern.matches("^[A-Za-z0-9_-]{43}$", devToken)).isTrue();

        // Verify SHA-256 hash in database
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        PasswordResetToken storedToken = tokens.stream()
                .filter(t -> t.getUser().getEmail().equalsIgnoreCase(testEmail))
                .findFirst()
                .orElseThrow();

        assertThat(storedToken.getTokenHash()).isNotEqualTo(devToken);
        assertThat(storedToken.getTokenHash()).hasSize(43); // SHA-256 Base64 URL-safe
        assertThat(storedToken.isUsed()).isFalse();
        assertThat(storedToken.getExpiresAt()).isAfter(LocalDateTime.now());

        // Successfully consume token
        authService.resetPassword(ResetPasswordRequest.builder()
                .token(devToken)
                .newPassword("NewSuperSecretPassword456!")
                .confirmPassword("NewSuperSecretPassword456!")
                .build());

        // Single-use enforcement: second consumption MUST fail
        assertThatThrownBy(() -> authService.resetPassword(ResetPasswordRequest.builder()
                .token(devToken)
                .newPassword("AnotherPassword789!")
                .confirmPassword("AnotherPassword789!")
                .build()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid or expired password reset token");
    }

    @Test
    @DisplayName("Rate limiter accurately calculates Retry-After from oldest sliding-window timestamp")
    void testSlidingWindowRetryAfterCalculation() throws InterruptedException {
        String key = "test-ip-rate-limit:" + System.currentTimeMillis();
        int maxRequests = 3;
        int windowSeconds = 5;

        // First 3 permits must succeed
        RateLimiterService.RateLimitResult res1 = rateLimiterService.tryAcquireWithResult(key, maxRequests, windowSeconds);
        assertThat(res1.allowed()).isTrue();
        assertThat(res1.retryAfterSeconds()).isEqualTo(0);

        Thread.sleep(50);
        RateLimiterService.RateLimitResult res2 = rateLimiterService.tryAcquireWithResult(key, maxRequests, windowSeconds);
        assertThat(res2.allowed()).isTrue();

        Thread.sleep(50);
        RateLimiterService.RateLimitResult res3 = rateLimiterService.tryAcquireWithResult(key, maxRequests, windowSeconds);
        assertThat(res3.allowed()).isTrue();

        // 4th permit exceeds limit
        RateLimiterService.RateLimitResult res4 = rateLimiterService.tryAcquireWithResult(key, maxRequests, windowSeconds);
        assertThat(res4.allowed()).isFalse();
        // Retry-After must be positive and bounded by the 5-second window
        assertThat(res4.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
        assertThat(res4.retryAfterSeconds()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("RateLimitingFilter sets dynamic Retry-After header and body upon reaching threshold")
    void testRateLimitingFilterHttpIntegration() throws Exception {
        String testIp = "198.51.100.42";

        // Login threshold is 15 req/60s per IP
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", testIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"nonexistent@tabufinery.io\",\"password\":\"invalid\"}"))
                    .andExpect(status().isBadRequest());
        }

        // 16th request must trigger 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", testIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nonexistent@tabufinery.io\",\"password\":\"invalid\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
                .andExpect(jsonPath("$.retryAfterSeconds").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("Admin user list bounded pagination respects default and max page size")
    void testAdminUserListBoundedPagination() {
        // Register multiple users
        for (int i = 0; i < 5; i++) {
            authService.signup(SignupRequest.builder()
                    .name("Pagination User " + i)
                    .email("page_user_" + i + "_" + System.currentTimeMillis() + "@tabufinery.io")
                    .password("Password123!")
                    .build());
        }

        // 1. Page size = 2 must return at most 2 users
        List<AdminUserDto> page1 = adminService.listUsers(0, 2);
        assertThat(page1).hasSize(2);

        // 2. Page 1 with size 2 must return next 2 users
        List<AdminUserDto> page2 = adminService.listUsers(1, 2);
        assertThat(page2).hasSize(2);
        assertThat(page1.get(0).getId()).isNotEqualTo(page2.get(0).getId());

        // 3. Oversized request (size=500) must be capped by MAX_PAGE_SIZE (100)
        List<AdminUserDto> capped = adminService.listUsers(0, 500);
        assertThat(capped.size()).isLessThanOrEqualTo(AdminService.MAX_PAGE_SIZE);

        // 4. Default pagination (page=0, size=50) works cleanly
        List<AdminUserDto> defaultList = adminService.listUsers();
        assertThat(defaultList.size()).isLessThanOrEqualTo(AdminService.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("CORS strictly allows only configured origins (localhost:5173 and 192.168.29.46:5173)")
    void testCorsStrictAllowedOrigins() throws Exception {
        // 1. Localhost:5173 allowed
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        // 2. 192.168.29.46:5173 allowed
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://192.168.29.46:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.29.46:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        // 3. Unconfigured origin rejected (no allow header returned)
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://unauthorized-domain.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
