package com.aditya.dataautomation;

import com.aditya.dataautomation.auth.dto.AuthResponse;
import com.aditya.dataautomation.auth.dto.SignupRequest;
import com.aditya.dataautomation.auth.service.AuthService;
import com.aditya.dataautomation.dashboard.model.Dashboard;
import com.aditya.dataautomation.dashboard.model.DashboardWidget;
import com.aditya.dataautomation.dashboard.model.WidgetType;
import com.aditya.dataautomation.dashboard.service.DashboardService;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.aditya.dataautomation.service.WorkbookSessionHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class MultiFileAndDashboardSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private WorkbookSessionHolder workbookSessionHolder;

    @Autowired
    private DashboardService dashboardService;

    private String tokenUserA;
    private Long idUserA;

    private String tokenUserB;
    private Long idUserB;

    @BeforeEach
    void setupUsers() {
        workbookSessionHolder.clearAll();

        String emailA = "usera_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse resA = authService.signup(new SignupRequest(emailA, "Password123!", "Password123!", "User A"));
        tokenUserA = resA.getToken();
        idUserA = resA.getUser().getId();

        String emailB = "userb_" + System.currentTimeMillis() + "@tabufinery.io";
        AuthResponse resB = authService.signup(new SignupRequest(emailB, "Password123!", "Password123!", "User B"));
        tokenUserB = resB.getToken();
        idUserB = resB.getUser().getId();
    }

    private ExcelWorkbookData createWorkbook(String fileName, String sheetName, List<String> headers, List<Map<String, Object>> rows) {
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName(sheetName)
                .headers(headers)
                .rows(rows)
                .build();
        return ExcelWorkbookData.builder()
                .fileName(fileName)
                .sheets(List.of(sheet))
                .build();
    }

    @Test
    @DisplayName("User A uploads multiple files; both remain in file list and switching works")
    void testMultiFile_UserACanUploadAndSwitch() throws Exception {
        ExcelWorkbookData salesWb = createWorkbook("Sales.xlsx", "SalesSheet", List.of("Product", "Revenue"),
                List.of(Map.of("Product", "Laptop", "Revenue", 1000L)));
        ExcelWorkbookData employeesWb = createWorkbook("Employees.xlsx", "EmpSheet", List.of("Name", "Salary"),
                List.of(Map.of("Name", "Alice", "Salary", 50000L)));

        // Register Sales directly into user A's workspace
        String fileIdSales = workbookSessionHolder.registerNewWorkbook(idUserA, salesWb, "Sales.xlsx", 1024L, "Initial Upload");
        // Register Employees into user A's workspace
        String fileIdEmployees = workbookSessionHolder.registerNewWorkbook(idUserA, employeesWb, "Employees.xlsx", 2048L, "Initial Upload");

        // Verify listing files for User A
        mockMvc.perform(get("/api/v1/workbook/files")
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fileName").value("Sales.xlsx"))
                .andExpect(jsonPath("$[1].fileName").value("Employees.xlsx"));

        // Currently active should be Employees (latest)
        mockMvc.perform(get("/api/v1/workbook/status")
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.fileName").value("Employees.xlsx"))
                .andExpect(jsonPath("$.sheets[0].sheetName").value("EmpSheet"));

        // Switch to Sales
        mockMvc.perform(post("/api/v1/workbook/files/" + fileIdSales + "/select")
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.fileName").value("Sales.xlsx"));

        // Verify status now reflects Sales
        mockMvc.perform(get("/api/v1/workbook/status")
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("Sales.xlsx"))
                .andExpect(jsonPath("$.sheets[0].sheetName").value("SalesSheet"));
    }

    @Test
    @DisplayName("User B cannot access or switch to User A's uploaded file")
    void testFileIsolation_UserBCannotAccessUserAFiles() throws Exception {
        ExcelWorkbookData salesWb = createWorkbook("Sales.xlsx", "SalesSheet", List.of("Product", "Revenue"),
                List.of(Map.of("Product", "Laptop", "Revenue", 1000L)));

        // User A uploads Sales
        String fileIdSales = workbookSessionHolder.registerNewWorkbook(idUserA, salesWb, "Sales.xlsx", 1024L, "Upload");

        // User B checks their file list -> should be empty
        mockMvc.perform(get("/api/v1/workbook/files")
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // User B tries to switch to User A's fileId -> 404
        mockMvc.perform(post("/api/v1/workbook/files/" + fileIdSales + "/select")
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // User B tries to delete User A's fileId -> 404
        mockMvc.perform(delete("/api/v1/workbook/files/" + fileIdSales)
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dashboard Ownership: User B cannot access, add widgets to, or delete User A's dashboard")
    void testDashboardOwnership_UserBCannotAccessUserADashboard() throws Exception {
        ExcelWorkbookData salesWb = createWorkbook("Sales.xlsx", "SalesSheet", List.of("Product", "Revenue"),
                List.of(Map.of("Product", "Laptop", "Revenue", 1000L)));

        String fileIdSales = workbookSessionHolder.registerNewWorkbook(idUserA, salesWb, "Sales.xlsx", 1024L, "Upload");

        // User A creates dashboard
        Dashboard dashA = dashboardService.createDashboard(idUserA, fileIdSales, "Sales.xlsx", "Sales Board", "SalesSheet", salesWb);
        String dashboardId = dashA.getDashboardId();

        // User A can access dashboard
        mockMvc.perform(get("/api/v1/dashboard/" + dashboardId)
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dashboardId").value(dashboardId))
                .andExpect(jsonPath("$.name").value("Sales Board"));

        // User B tries to get User A's dashboard -> 400 with "Dashboard not found or access denied"
        mockMvc.perform(get("/api/v1/dashboard/" + dashboardId)
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dashboard not found or access denied"));

        // User B tries to add widget to User A's dashboard -> 400 access denied
        String widgetPayload = """
                {
                    "dashboardId": "%s",
                    "type": "KPI",
                    "title": "Hacked KPI",
                    "sheetName": "SalesSheet",
                    "columnName": "Revenue",
                    "aggregation": "SUM"
                }
                """.formatted(dashboardId);

        mockMvc.perform(post("/api/v1/dashboard/widget")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenUserB)
                        .content(widgetPayload))
                .andExpect(status().isBadRequest());

        // User B tries to delete User A's dashboard -> 400 access denied
        mockMvc.perform(delete("/api/v1/dashboard/" + dashboardId)
                        .header("Authorization", "Bearer " + tokenUserB))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Dashboard association: Sales dashboard keeps using Sales workbook even if active workbook changes")
    void testDashboardAssociation_KeepsAssociatedWorkbook() throws Exception {
        ExcelWorkbookData salesWb = createWorkbook("Sales.xlsx", "SalesSheet", List.of("Product", "Revenue"),
                List.of(Map.of("Product", "Laptop", "Revenue", 1000L), Map.of("Product", "Mouse", "Revenue", 500L)));
        ExcelWorkbookData empWb = createWorkbook("Employees.xlsx", "EmpSheet", List.of("Name", "Salary"),
                List.of(Map.of("Name", "Bob", "Salary", 4000L)));

        String fileIdSales = workbookSessionHolder.registerNewWorkbook(idUserA, salesWb, "Sales.xlsx", 1024L, "Upload");
        String fileIdEmp = workbookSessionHolder.registerNewWorkbook(idUserA, empWb, "Employees.xlsx", 2048L, "Upload");

        // User A creates dashboard bound to Sales.xlsx
        Dashboard dashA = dashboardService.createDashboard(idUserA, fileIdSales, "Sales.xlsx", "Sales Metrics", "SalesSheet", salesWb);
        DashboardWidget widget = DashboardWidget.builder()
                .type(WidgetType.KPI)
                .title("Total Revenue")
                .sheetName("SalesSheet")
                .columnName("Revenue")
                .aggregation("SUM")
                .build();
        dashboardService.addWidget(idUserA, dashA.getDashboardId(), widget, salesWb);

        // Fetching dashboard should still safely compute against Sales.xlsx without failing or mixing
        mockMvc.perform(get("/api/v1/dashboard/" + dashA.getDashboardId())
                        .header("Authorization", "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sales Metrics"))
                .andExpect(jsonPath("$.widgets[0].kpiValue").value(1500));
    }
}
