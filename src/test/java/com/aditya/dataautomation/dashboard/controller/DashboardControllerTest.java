package com.aditya.dataautomation.dashboard.controller;

import com.aditya.dataautomation.dashboard.model.Dashboard;
import com.aditya.dataautomation.dashboard.model.WidgetType;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkbookSessionHolder sessionHolder;

    @BeforeEach
    void setUp() {
        sessionHolder.clearAll();

        List<String> headers = List.of("OrderDate", "Category", "City", "Quantity", "Sales");
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("OrderDate", "2026-01-15");
        r1.put("Category", "Technology");
        r1.put("City", "Mumbai");
        r1.put("Quantity", 4L);
        r1.put("Sales", 45000L);
        rows.add(r1);

        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("OrderDate", "2026-01-20");
        r2.put("Category", "Furniture");
        r2.put("City", "Bangalore");
        r2.put("Quantity", 2L);
        r2.put("Sales", 15000L);
        rows.add(r2);

        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Orders")
                .headers(headers)
                .rows(rows)
                .build();

        ExcelWorkbookData wb = ExcelWorkbookData.builder()
                .fileName("company_sales.xlsx")
                .sheets(List.of(sheet))
                .build();

        sessionHolder.store(wb);
    }

    @Test
    @WithMockUser(username = "analyst@tabufinery.io")
    @DisplayName("POST /api/v1/dashboard/auto-generate creates and populates dashboard widgets")
    void testAutoGenerateEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/auto-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sheetName\":\"Orders\",\"name\":\"Executive Overview\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Executive Overview"))
                .andExpect(jsonPath("$.sheetName").value("Orders"))
                .andExpect(jsonPath("$.widgets").isArray())
                .andExpect(jsonPath("$.widgets.length()").value(org.hamcrest.Matchers.greaterThan(2)));
    }

    @Test
    @WithMockUser(username = "analyst@tabufinery.io")
    @DisplayName("POST /api/v1/dashboard/auto-generate returns 400 when sheetName is missing")
    void testAutoGenerateMissingSheetName() throws Exception {
        mockMvc.perform(post("/api/v1/dashboard/auto-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing Sheet\"}"))
                .andExpect(status().isBadRequest());
    }
}
