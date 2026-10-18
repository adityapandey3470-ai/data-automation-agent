package com.aditya.dataautomation.workbook.controller;

import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import com.aditya.dataautomation.workbook.service.WorkbookSessionHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkbookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookSessionHolder workbookSessionHolder;

    @Autowired
    private com.aditya.dataautomation.auth.repository.UserRepository userRepository;

    @BeforeEach
    void setUp() {
        workbookSessionHolder.clearAll();
    }

    @Test
    @DisplayName("GET /api/v1/workbook/status should be publicly accessible and return loaded=false when empty")
    void testGetStatus_WhenEmpty_ReturnsLoadedFalse() throws Exception {
        mockMvc.perform(get("/api/v1/workbook/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(false))
                .andExpect(jsonPath("$.canUndo").value(false))
                .andExpect(jsonPath("$.canRedo").value(false));
    }

    @Test
    @WithMockUser(username = "workbook_tester@tabufinery.io")
    @DisplayName("GET /api/v1/workbook/status should return loaded=true and details when workbook is stored")
    void testGetStatus_WhenWorkbookStored_ReturnsDetails() throws Exception {
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Sales")
                .headers(List.of("Product", "Revenue"))
                .rows(List.of(Map.of("Product", "Widget", "Revenue", 100)))
                .build();

        ExcelWorkbookData workbook = ExcelWorkbookData.builder()
                .fileName("QuarterlyReport.xlsx")
                .sheets(List.of(sheet))
                .build();

        workbookSessionHolder.store(workbook, "Initial Upload");

        mockMvc.perform(get("/api/v1/workbook/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.fileName").value("QuarterlyReport.xlsx"))
                .andExpect(jsonPath("$.sheets[0].sheetName").value("Sales"))
                .andExpect(jsonPath("$.sheets[0].rowCount").value(1))
                .andExpect(jsonPath("$.canUndo").value(false))
                .andExpect(jsonPath("$.canRedo").value(false));
    }

    @Test
    @WithMockUser(username = "workbook_tester@tabufinery.io")
    @DisplayName("GET /api/v1/workbook/status should safely handle null fileName and null sheets without HTTP 500 NPE")
    void testGetStatus_WithNullFields_DoesNotThrow500() throws Exception {
        ExcelWorkbookData workbookWithNulls = ExcelWorkbookData.builder()
                .fileName(null)
                .sheets(null)
                .build();

        workbookSessionHolder.store(workbookWithNulls, "Edge Case Store");

        mockMvc.perform(get("/api/v1/workbook/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(true))
                .andExpect(jsonPath("$.fileName").value("Active Workbook"))
                .andExpect(jsonPath("$.sheets").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/workbook/status when unauthenticated should NEVER expose an authenticated user's stored workbook")
    void testAnonymousCannotAccessStoredWorkbook() throws Exception {
        com.aditya.dataautomation.auth.entity.User owner = userRepository.findByEmailIgnoreCase("owner_test@tabufinery.io")
                .orElseGet(() -> userRepository.save(com.aditya.dataautomation.auth.entity.User.builder()
                        .email("owner_test@tabufinery.io")
                        .name("Owner")
                        .passwordHash("$2a$10$none")
                        .role("ROLE_USER")
                        .enabled(true)
                        .build()));

        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Confidential")
                .headers(List.of("Salary", "SSN"))
                .rows(List.of(Map.of("Salary", 50000)))
                .build();
        ExcelWorkbookData workbook = ExcelWorkbookData.builder()
                .fileName("Payroll.xlsx")
                .sheets(List.of(sheet))
                .build();

        workbookSessionHolder.registerNewWorkbook(owner.getId(), workbook, "Payroll.xlsx", 2048L, "Secret Upload");

        // Make an unauthenticated request to /status
        mockMvc.perform(get("/api/v1/workbook/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loaded").value(false))
                .andExpect(jsonPath("$.fileId").isEmpty())
                .andExpect(jsonPath("$.files").isEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/workbook/undo and redo should safely handle empty state")
    void testUndoRedo_WhenEmpty_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/workbook/undo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Nothing to undo"));

        mockMvc.perform(post("/api/v1/workbook/redo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Nothing to redo"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/workbook/history should return empty history safely")
    void testGetHistory_ReturnsHistoryList() throws Exception {
        mockMvc.perform(get("/api/v1/workbook/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history").isArray());
    }
}
