package com.aditya.dataautomation;

import com.aditya.dataautomation.ai.model.DataProfileResponse;
import com.aditya.dataautomation.ai.model.DataQualityReport;
import com.aditya.dataautomation.ai.service.DataInsightsService;
import com.aditya.dataautomation.auth.dto.AuthResponse;
import com.aditya.dataautomation.auth.dto.LoginRequest;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.service.AuthService;
import com.aditya.dataautomation.export.service.PdfReportExportService;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.project.dto.ProjectDetailDto;
import com.aditya.dataautomation.project.dto.ProjectSummaryDto;
import com.aditya.dataautomation.project.dto.RenameProjectRequest;
import com.aditya.dataautomation.project.dto.SaveProjectRequest;
import com.aditya.dataautomation.project.service.ProjectService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aditya.dataautomation.auth.entity.User;
import com.aditya.dataautomation.auth.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthAndProjectPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private WorkbookSessionHolder sessionHolder;

    @Autowired
    private DataInsightsService dataInsightsService;

    @Autowired
    private PdfReportExportService pdfReportExportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Long user1Id;
    private Long user2Id;

    @BeforeEach
    void setUp() {
        sessionHolder.clear();
        String u1Email = "user1_" + System.currentTimeMillis() + "@tabufinery.io";
        String u2Email = "user2_" + System.currentTimeMillis() + "@tabufinery.io";

        AuthResponse r1 = authService.signup(new SignupRequest(u1Email, "secret123", "User One"));
        user1Id = r1.getUser().getId();

        AuthResponse r2 = authService.signup(new SignupRequest(u2Email, "secret456", "User Two"));
        user2Id = r2.getUser().getId();
    }

    @Test
    @DisplayName("Auth: Signup and Login verify BCrypt password hashing and JWT issuance")
    void testAuthWorkflow() {
        String email = "alice_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse signupRes = authService.signup(new SignupRequest(email, "mypassword", "Alice"));
        assertThat(signupRes.getToken()).isNotBlank();
        assertThat(signupRes.getUser().getEmail()).isEqualTo(email);

        // Duplicate signup rejected
        assertThatThrownBy(() -> authService.signup(new SignupRequest(email, "mypassword", "Alice Duplicate")))
                .hasMessageContaining("already exists");

        // Valid login
        AuthResponse loginRes = authService.login(new LoginRequest(email, "mypassword"));
        assertThat(loginRes.getToken()).isNotBlank();
        assertThat(loginRes.getUser().getName()).isEqualTo("Alice");

        // Invalid login password
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "wrongpass")))
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Projects: Save, list, multi-tenant isolation, reopen, rename, and delete")
    @Transactional
    void testProjectLifecycleAndIsolation() {
        // 1. Setup session workbook
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Sales")
                .headers(List.of("Region", "Revenue"))
                .rows(List.of(
                        Map.of("Region", "North", "Revenue", 1000L),
                        Map.of("Region", "South", "Revenue", 2500L),
                        Map.of("Region", "North", "Revenue", 1000L) // duplicate
                ))
                .build();
        ExcelWorkbookData workbook = ExcelWorkbookData.builder()
                .fileName("Q3_Sales.xlsx")
                .sheets(List.of(sheet))
                .build();

        sessionHolder.storeForUser(user1Id, workbook, "Initial Upload");

        // 2. User 1 saves project
        SaveProjectRequest saveReq = SaveProjectRequest.builder()
                .name("Q3 Regional Performance")
                .description("Regional breakdown of revenues")
                .activeSheetName("Sales")
                .build();
        ProjectSummaryDto saved = projectService.saveCurrentProject(user1Id, saveReq);
        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getName()).isEqualTo("Q3 Regional Performance");
        assertThat(saved.getRowCount()).isEqualTo(3);
        assertThat(saved.getColumnCount()).isEqualTo(2);

        // 3. User 1 lists projects -> sees 1 project
        List<ProjectSummaryDto> u1Projects = projectService.listUserProjects(user1Id);
        assertThat(u1Projects).hasSize(1);
        assertThat(u1Projects.get(0).getName()).isEqualTo("Q3 Regional Performance");

        // 4. Multi-tenancy check: User 2 lists projects -> sees 0 projects
        List<ProjectSummaryDto> u2Projects = projectService.listUserProjects(user2Id);
        assertThat(u2Projects).isEmpty();

        // 5. User 2 cannot access or rename User 1's project
        assertThatThrownBy(() -> projectService.getProject(user2Id, saved.getId()))
                .hasMessageContaining("not found or access denied");
        assertThatThrownBy(() -> projectService.renameProject(user2Id, saved.getId(), new RenameProjectRequest("Hacked")))
                .hasMessageContaining("not found or access denied");

        // 6. User 1 renames project
        ProjectSummaryDto renamed = projectService.renameProject(user1Id, saved.getId(), new RenameProjectRequest("Q3 Regional Performance Updated"));
        assertThat(renamed.getName()).isEqualTo("Q3 Regional Performance Updated");

        // 7. Clear memory session and reopen saved project
        sessionHolder.clearForUser(user1Id);
        assertThat(sessionHolder.getForUser(user1Id)).isNull();

        ProjectDetailDto opened = projectService.openProject(user1Id, saved.getId());
        assertThat(opened.getName()).isEqualTo("Q3 Regional Performance Updated");
        assertThat(sessionHolder.getForUser(user1Id)).isNotNull();
        assertThat(sessionHolder.getForUser(user1Id).getSheets().get(0).getRowCount()).isEqualTo(3);

        // 8. Delete project
        projectService.deleteProject(user1Id, saved.getId());
        assertThat(projectService.listUserProjects(user1Id)).isEmpty();
    }

    @Test
    @DisplayName("AI Data Quality Score: Computes 0-100 score, dimensions, and actionable issues")
    void testDataQualityScoreCalculation() {
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("TestSheet")
                .headers(List.of("ID", "Customer", "Amount"))
                .rows(List.of(
                        Map.of("ID", 1L, "Customer", "Acme Corp  ", "Amount", 100.0),
                        Map.of("ID", 2L, "Customer", "Beta Inc", "Amount", 200.0),
                        Map.of("ID", 1L, "Customer", "Acme Corp  ", "Amount", 100.0) // duplicate row & untrimmed
                ))
                .build();

        DataProfileResponse profile = dataInsightsService.generateProfile(sheet);
        assertThat(profile).isNotNull();

        DataQualityReport quality = profile.getQualityReport();
        assertThat(quality).isNotNull();
        assertThat(quality.getOverallScore()).isBetween(0, 100);
        assertThat(quality.getGrade()).isIn("A+", "A", "B", "C", "D", "F");
        assertThat(quality.getCompletenessScore()).isBetween(0, 100);
        assertThat(quality.getUniquenessScore()).isLessThanOrEqualTo(100);
        assertThat(quality.getConsistencyScore()).isLessThanOrEqualTo(100);
        assertThat(quality.getIssues()).isNotEmpty();

        // Check actionable commands in detected issues
        boolean hasDeduplicate = quality.getIssues().stream().anyMatch(i -> "REMOVE_DUPLICATES".equals(i.getOperation()));
        assertThat(hasDeduplicate).isTrue();
    }

    @Test
    @DisplayName("PDF Report: Generates non-empty branded PDF document containing quality and metrics")
    void testPdfReportExport() {
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Metrics")
                .headers(List.of("Product", "Sales"))
                .rows(List.of(
                        Map.of("Product", "Widget A", "Sales", 500L),
                        Map.of("Product", "Widget B", "Sales", 1200L)
                ))
                .build();
        ExcelWorkbookData workbook = ExcelWorkbookData.builder()
                .fileName("Sales_2026.xlsx")
                .sheets(List.of(sheet))
                .build();

        byte[] pdfBytes = pdfReportExportService.generateExecutivePdfReport(workbook, "Metrics");
        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000); // Standard PDF binary is several KB

        // Valid PDF header (%PDF-)
        String header = new String(pdfBytes, 0, 5);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("HTTP Security: Unauthenticated request to protected project routes returns 401")
    void testUnauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("HTTP End-to-End: Signup, Login, Bearer Token Auth, and PDF Report Export via HTTP")
    void testEndToEndHttpEndpoints() throws Exception {
        String email = "httpuser_" + System.currentTimeMillis() + "@tabufinery.io";

        // 1. Signup via HTTP
        MvcResult signupRes = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"pass1234\",\"name\":\"Http Tester\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.name").value("Http Tester"))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(signupRes.getResponse().getContentAsString(), "$.token");
        Number userIdNum = com.jayway.jsonpath.JsonPath.read(signupRes.getResponse().getContentAsString(), "$.user.id");
        Long userId = userIdNum.longValue();
        assertThat(token).isNotBlank();

        // 2. Access /api/v1/auth/me with Bearer token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // 3. Setup workbook in user's isolated session and export PDF via HTTP
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("ReportSheet")
                .headers(List.of("Category", "Spend"))
                .rows(List.of(Map.of("Category", "Cloud", "Spend", 3200L)))
                .build();
        ExcelWorkbookData workbook = ExcelWorkbookData.builder()
                .fileName("Cloud_Spend.xlsx")
                .sheets(List.of(sheet))
                .build();
        sessionHolder.storeForUser(userId, workbook, "Cloud Spend Data");

        mockMvc.perform(get("/api/v1/export/report/pdf?sheetName=ReportSheet")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    byte[] bytes = result.getResponse().getContentAsByteArray();
                    assertThat(bytes).isNotEmpty();
                    assertThat(new String(bytes, 0, 5)).isEqualTo("%PDF-");
                });

        // 4. Save project via HTTP with Bearer token
        mockMvc.perform(post("/api/v1/projects/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cloud Spend Analysis\",\"activeSheetName\":\"ReportSheet\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cloud Spend Analysis"))
                .andExpect(jsonPath("$.fileName").value("Cloud_Spend.xlsx"));

        // 5. List projects via HTTP with Bearer token
        mockMvc.perform(get("/api/v1/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cloud Spend Analysis"));
    }

    @Test
    @DisplayName("Admin Security: Regular users receive 403; Admins receive 200 and can manage users")
    void testAdminSecurityAndManagement() throws Exception {
        // 1. Regular user signup
        String userEmail = "reguser_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse userAuth = authService.signup(new SignupRequest(userEmail, "userpass123", "Regular User"));
        String userToken = userAuth.getToken();

        // Regular user blocked from admin APIs (403 Forbidden)
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. Create Admin user in DB
        String adminEmail = "admin_" + System.currentTimeMillis() + "@tabufinery.io";
        User adminUser = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode("adminsecret"))
                .name("Super Admin")
                .role("ROLE_ADMIN")
                .enabled(true)
                .build();
        adminUser = userRepository.save(adminUser);

        AuthResponse adminAuth = authService.login(new LoginRequest(adminEmail, "adminsecret"));
        String adminToken = adminAuth.getToken();
        assertThat(adminAuth.getUser().getRole()).isEqualTo("ROLE_ADMIN");

        // 3. Admin can access stats & user list
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").isNumber())
                .andExpect(jsonPath("$.activeUsers").isNumber());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 4. Admin disables regular user
        mockMvc.perform(put("/api/v1/admin/users/" + userAuth.getUser().getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // 5. Disabled user can no longer log in
        assertThatThrownBy(() -> authService.login(new LoginRequest(userEmail, "userpass123")))
                .hasMessageContaining("disabled");

        // 6. Admin re-enables regular user
        mockMvc.perform(put("/api/v1/admin/users/" + userAuth.getUser().getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Re-enabled user can log in
        AuthResponse reLogin = authService.login(new LoginRequest(userEmail, "userpass123"));
        assertThat(reLogin.getToken()).isNotBlank();
    }

    @Test
    @DisplayName("Password Reset Workflow: Request reset -> get dev token / verify DB -> reset password -> login with new password")
    void testPasswordResetWorkflow() throws Exception {
        String email = "resetuser_" + System.currentTimeMillis() + "@tabufinery.io";
        authService.signup(new SignupRequest(email, "oldpassword123", "Reset User"));

        // 1. Request password reset via HTTP
        MvcResult forgotResult = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString())
                .andReturn();

        // In test mode (no SMTP configured), devResetToken is returned for verification
        String devToken = com.jayway.jsonpath.JsonPath.read(forgotResult.getResponse().getContentAsString(), "$.devResetToken");
        assertThat(devToken).isNotBlank();

        // 2. Perform password reset with devToken
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + devToken + "\",\"newPassword\":\"newSecret123\",\"confirmPassword\":\"newSecret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 3. Old password is rejected
        assertThatThrownBy(() -> authService.login(new LoginRequest(email, "oldpassword123")))
                .hasMessageContaining("Invalid email or password");

        // 4. New password logs in successfully
        AuthResponse loginRes = authService.login(new LoginRequest(email, "newSecret123"));
        assertThat(loginRes.getToken()).isNotBlank();
        assertThat(loginRes.getUser().getEmail()).isEqualTo(email);
    }
}
