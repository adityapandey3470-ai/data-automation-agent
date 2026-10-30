package com.aditya.dataautomation.auth;

import com.aditya.dataautomation.auth.dto.AuthResponse;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.security.RateLimiterService;
import com.aditya.dataautomation.auth.service.AuthService;
import com.aditya.dataautomation.auth.service.JwtService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Priority1SecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkbookSessionHolder workbookSessionHolder;

    @Autowired
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService.reset();
        workbookSessionHolder.clearAll();
    }

    // =========================================================================
    // 1. JWT Claim Extraction Security Tests
    // =========================================================================

    @Test
    @DisplayName("Valid JWT correctly extracts userId, email, and role")
    void testValidJwtClaimsExtraction() {
        String token = jwtService.generateToken(42L, "valid@tabufinery.io", "Valid User", "ROLE_USER");
        assertThat(jwtService.validateToken(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractEmail(token)).isEqualTo("valid@tabufinery.io");
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("Tampered JWT payload fails validation and returns null for all security claims")
    void testTamperedJwtPayloadReturnsNull() {
        String token = jwtService.generateToken(42L, "regular@tabufinery.io", "Regular User", "ROLE_USER");
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        // Forge the payload to claim admin userId=1 and ROLE_ADMIN
        String forgedPayloadJson = "{\"sub\":\"1\",\"email\":\"admin@tabufinery.io\",\"name\":\"Admin\",\"role\":\"ROLE_ADMIN\",\"iat\":9999999999,\"exp\":9999999999}";
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayloadJson.getBytes());
        String forgedToken = parts[0] + "." + forgedPayload + "." + parts[2];

        // Ensure validateToken rejects it
        assertThat(jwtService.validateToken(forgedToken)).isFalse();

        // Crucial: claim extractors MUST return null, never extracting forged claims
        assertThat(jwtService.extractUserId(forgedToken)).isNull();
        assertThat(jwtService.extractEmail(forgedToken)).isNull();
        assertThat(jwtService.extractRole(forgedToken)).isNull();
    }

    @Test
    @DisplayName("Malformed or empty JWT tokens safely return null without throwing unhandled exceptions")
    void testMalformedTokensReturnNull() {
        assertThat(jwtService.extractUserId(null)).isNull();
        assertThat(jwtService.extractUserId("")).isNull();
        assertThat(jwtService.extractUserId("not.a.token")).isNull();
        assertThat(jwtService.extractEmail("invalid.jwt.token")).isNull();
        assertThat(jwtService.extractRole("malformed")).isNull();
    }

    // =========================================================================
    // 2. IP Spoofing Prevention in RateLimitingFilter
    // =========================================================================

    @Test
    @DisplayName("Rate limiter ignores spoofed X-Forwarded-For when not behind trusted proxy, preventing rate limit bypass")
    void testIpSpoofingCannotBypassRateLimit() throws Exception {
        String clientIp = "203.0.113.88";

        // Login rate limit is 15 requests per 60s per client IP
        for (int i = 0; i < 15; i++) {
            // Attacker sends a different fake X-Forwarded-For header on every request trying to bypass rate limiting
            String spoofedHeader = "10.0.0." + (i + 1);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nonexistent@tabufinery.io\",\"password\":\"WrongPass123!\"}")
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            })
                            .header("X-Forwarded-For", spoofedHeader))
                    .andExpect(status().isBadRequest());
        }

        // 16th request from the same remote IP MUST be blocked with HTTP 429 Too Many Requests,
        // even with yet another different spoofed X-Forwarded-For header!
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nonexistent@tabufinery.io\",\"password\":\"WrongPass123!\"}")
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        })
                        .header("X-Forwarded-For", "192.168.1.99"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
    }

    // =========================================================================
    // 3. Anonymous and Cross-User Data Isolation Tests
    // =========================================================================

    @Test
    @DisplayName("Unauthenticated visitor calling /workbook/status never receives another user's workbook data")
    void testAnonymousVisitorReceivesEmptyStatus() throws Exception {
        // User A registers and uploads a confidential workbook
        String emailA = "user_a_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse resA = authService.signup(new SignupRequest(emailA, "Pass1234!", "Pass1234!", "User A"));
        Long userIdA = resA.getUser().getId();

        ExcelWorkbookData confidentialWorkbook = ExcelWorkbookData.builder()
                .fileName("Confidential_Payroll.xlsx")
                .sheets(List.of(ExcelSheetData.builder()
                        .sheetName("Salaries")
                        .headers(List.of("Employee", "Salary"))
                        .rows(List.of(Map.of("Employee", "Alice", "Salary", 150000)))
                        .build()))
                .build();

        workbookSessionHolder.registerNewWorkbook(userIdA, confidentialWorkbook, "Confidential_Payroll.xlsx", 2048L, "Initial Upload");

        // Anonymous user visits /api/v1/workbook/status without any auth token
        mockMvc.perform(get("/api/v1/workbook/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(false))
                .andExpect(jsonPath("$.fileId").isEmpty())
                .andExpect(jsonPath("$.files").isEmpty());

        // Authenticated User A visits /api/v1/workbook/status and DOES see their workbook
        mockMvc.perform(get("/api/v1/workbook/status")
                        .header("Authorization", "Bearer " + resA.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.fileName").value("Confidential_Payroll.xlsx"))
                .andExpect(jsonPath("$.sheets[0].sheetName").value("Salaries"));
    }

    @Test
    @DisplayName("User B cannot see or access User A's workbook in /workbook/status")
    void testUserBCannotSeeUserAWorkbook() throws Exception {
        String emailA = "owner_a_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse resA = authService.signup(new SignupRequest(emailA, "Pass1234!", "Pass1234!", "Owner A"));

        String emailB = "intruder_b_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse resB = authService.signup(new SignupRequest(emailB, "Pass1234!", "Pass1234!", "User B"));

        ExcelWorkbookData wbA = ExcelWorkbookData.builder()
                .fileName("Company_Secrets.xlsx")
                .sheets(List.of(ExcelSheetData.builder()
                        .sheetName("Secrets")
                        .headers(List.of("Key", "Value"))
                        .rows(List.of(Map.of("Key", "API_KEY", "Value", "12345")))
                        .build()))
                .build();

        workbookSessionHolder.registerNewWorkbook(resA.getUser().getId(), wbA, "Company_Secrets.xlsx", 1024L, "Owner A Upload");

        // User B queries /workbook/status with User B's token
        mockMvc.perform(get("/api/v1/workbook/status")
                        .header("Authorization", "Bearer " + resB.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(false))
                .andExpect(jsonPath("$.fileId").isEmpty())
                .andExpect(jsonPath("$.files").isEmpty());
    }
}
