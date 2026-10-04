package com.aditya.dataautomation.controller;

import com.aditya.dataautomation.dto.excel.ExcelFileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelFileControllerTest {

    private static final String UPLOAD_URL = "/api/v1/files/excel";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    private byte[] createSimpleExcel(String sheetName, String[] headers, Object[][] data) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(sheetName);
            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            // Data rows
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < data[r].length; c++) {
                    Cell cell = row.createCell(c);
                    setCellValue(cell, data[r][c], workbook);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createMultiSheetExcel() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Sheet 1: Sales
            Sheet sales = workbook.createSheet("Sales");
            Row h1 = sales.createRow(0);
            h1.createCell(0).setCellValue("Product");
            h1.createCell(1).setCellValue("Revenue");
            Row d1 = sales.createRow(1);
            d1.createCell(0).setCellValue("Laptop");
            d1.createCell(1).setCellValue(120000);

            // Sheet 2: Inventory
            Sheet inventory = workbook.createSheet("Inventory");
            Row h2 = inventory.createRow(0);
            h2.createCell(0).setCellValue("Item");
            h2.createCell(1).setCellValue("Quantity");
            h2.createCell(2).setCellValue("InStock");
            Row d2 = inventory.createRow(1);
            d2.createCell(0).setCellValue("Mouse");
            d2.createCell(1).setCellValue(500);
            d2.createCell(2).setCellValue(true);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createEmptyWorkbook() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Empty");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createExcelWithMixedTypes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Mixed");

            // Headers
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Name");
            headerRow.createCell(1).setCellValue("Score");
            headerRow.createCell(2).setCellValue("Active");
            headerRow.createCell(3).setCellValue("JoinDate");
            headerRow.createCell(4).setCellValue("Notes");

            // Data row with mixed types
            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("Alice");          // String
            dataRow.createCell(1).setCellValue(95.5);             // Double
            dataRow.createCell(2).setCellValue(true);             // Boolean
            // Date cell
            Cell dateCell = dataRow.createCell(3);
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper helper = workbook.getCreationHelper();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
            dateCell.setCellStyle(dateStyle);
            dateCell.setCellValue(new Date(1693526400000L));       // 2023-09-01 approximately
            // Blank cell
            dataRow.createCell(4);                                // Blank

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createExcelWithBlankCells() throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Sparse");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("A");
            headerRow.createCell(1).setCellValue("B");
            headerRow.createCell(2).setCellValue("C");

            // Row with gaps
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("val1");
            // cell 1 intentionally skipped
            row1.createCell(2).setCellValue("val3");

            // Completely blank row
            sheet.createRow(2);

            // Another data row
            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("val4");
            row3.createCell(1).setCellValue("val5");
            row3.createCell(2).setCellValue("val6");

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createExcelWithManyRows(int totalRows) throws Exception {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("BigSheet");
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Index");
            headerRow.createCell(1).setCellValue("Value");

            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.createRow(i);
                row.createCell(0).setCellValue(i);
                row.createCell(1).setCellValue("Row_" + i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void setCellValue(Cell cell, Object value, Workbook workbook) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof String s) {
            cell.setCellValue(s);
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (value instanceof Date d) {
            CellStyle dateStyle = workbook.createCellStyle();
            CreationHelper helper = workbook.getCreationHelper();
            dateStyle.setDataFormat(helper.createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
            cell.setCellStyle(dateStyle);
            cell.setCellValue(d);
        }
    }


    @Nested
    @DisplayName("Valid .xlsx uploads")
    class ValidUploads {

        @Test
        @DisplayName("Should process a valid single-sheet Excel file")
        void shouldProcessValidExcelFile() throws Exception {
            byte[] bytes = createSimpleExcel("Sales",
                    new String[]{"Product", "Quantity", "Revenue"},
                    new Object[][]{
                            {"Laptop", 2, 120000},
                            {"Phone", 5, 250000}
                    });

            MockMultipartFile file = new MockMultipartFile(
                    "file", "sales.xlsx", XLSX_CONTENT_TYPE, bytes);

            MvcResult result = mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("sales.xlsx"))
                    .andExpect(jsonPath("$.sheetCount").value(1))
                    .andExpect(jsonPath("$.sheets[0].sheetName").value("Sales"))
                    .andExpect(jsonPath("$.sheets[0].rowCount").value(2))
                    .andExpect(jsonPath("$.sheets[0].columnCount").value(3))
                    .andExpect(jsonPath("$.sheets[0].headers[0]").value("Product"))
                    .andExpect(jsonPath("$.sheets[0].headers[1]").value("Quantity"))
                    .andExpect(jsonPath("$.sheets[0].headers[2]").value("Revenue"))
                    .andExpect(jsonPath("$.sheets[0].previewRows.length()").value(2))
                    .andReturn();

            // Verify deserialized response
            ExcelFileResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ExcelFileResponse.class);
            assertThat(response.getFileSize()).isGreaterThan(0);
            assertThat(response.getContentType()).isEqualTo(XLSX_CONTENT_TYPE);
        }

        @Test
        @DisplayName("Should process a workbook with multiple sheets")
        void shouldProcessMultipleSheets() throws Exception {
            byte[] bytes = createMultiSheetExcel();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "multi.xlsx", XLSX_CONTENT_TYPE, bytes);

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sheetCount").value(2))
                    .andExpect(jsonPath("$.sheets[0].sheetName").value("Sales"))
                    .andExpect(jsonPath("$.sheets[0].columnCount").value(2))
                    .andExpect(jsonPath("$.sheets[1].sheetName").value("Inventory"))
                    .andExpect(jsonPath("$.sheets[1].columnCount").value(3));
        }

        @Test
        @DisplayName("Should handle mixed cell types (string, numeric, boolean, date, blank)")
        void shouldHandleMixedCellTypes() throws Exception {
            byte[] bytes = createExcelWithMixedTypes();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "mixed.xlsx", XLSX_CONTENT_TYPE, bytes);

            MvcResult result = mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sheets[0].rowCount").value(1))
                    .andExpect(jsonPath("$.sheets[0].columnCount").value(5))
                    .andReturn();

            ExcelFileResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ExcelFileResponse.class);

            Map<String, Object> row = response.getSheets().get(0).getPreviewRows().get(0);
            assertThat(row.get("Name")).isEqualTo("Alice");
            assertThat(row.get("Score")).isEqualTo(95.5);
            assertThat(row.get("Active")).isEqualTo(true);
            assertThat(row.get("JoinDate")).isNotNull();         // Date string
            assertThat(row.get("Notes")).isNull();                // Blank cell
        }

        @Test
        @DisplayName("Should handle blank cells and sparse rows")
        void shouldHandleBlankCells() throws Exception {
            byte[] bytes = createExcelWithBlankCells();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "sparse.xlsx", XLSX_CONTENT_TYPE, bytes);

            MvcResult result = mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sheets[0].columnCount").value(3))
                    .andReturn();

            ExcelFileResponse response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ExcelFileResponse.class);

            List<Map<String, Object>> rows = response.getSheets().get(0).getPreviewRows();
            // First data row has a blank in column B
            assertThat(rows.get(0).get("A")).isEqualTo("val1");
            assertThat(rows.get(0).get("B")).isNull();
            assertThat(rows.get(0).get("C")).isEqualTo("val3");
        }

        @Test
        @DisplayName("Should limit preview rows to configured maximum")
        void shouldLimitPreviewRows() throws Exception {
            // Create 50 rows but preview should be capped at 10
            byte[] bytes = createExcelWithManyRows(50);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "large.xlsx", XLSX_CONTENT_TYPE, bytes);

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sheets[0].rowCount").value(50))
                    .andExpect(jsonPath("$.sheets[0].previewRows.length()").value(10));
        }

        @Test
        @DisplayName("Should process an empty sheet (no data, no headers)")
        void shouldHandleEmptySheet() throws Exception {
            byte[] bytes = createEmptyWorkbook();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty_sheet.xlsx", XLSX_CONTENT_TYPE, bytes);

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sheets[0].sheetName").value("Empty"))
                    .andExpect(jsonPath("$.sheets[0].rowCount").value(0))
                    .andExpect(jsonPath("$.sheets[0].columnCount").value(0))
                    .andExpect(jsonPath("$.sheets[0].headers").isEmpty())
                    .andExpect(jsonPath("$.sheets[0].previewRows").isEmpty());
        }

        @Test
        @DisplayName("Should accept file with application/octet-stream content type")
        void shouldAcceptOctetStreamContentType() throws Exception {
            byte[] bytes = createSimpleExcel("Data",
                    new String[]{"Col1"},
                    new Object[][]{{"value"}});

            MockMultipartFile file = new MockMultipartFile(
                    "file", "data.xlsx", "application/octet-stream", bytes);

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sheets[0].sheetName").value("Data"));
        }
    }


    @Nested
    @DisplayName("Invalid uploads")
    class InvalidUploads {

        @Test
        @DisplayName("Should reject when no file is provided")
        void shouldRejectMissingFile() throws Exception {
            mockMvc.perform(multipart(UPLOAD_URL))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject empty file")
        void shouldRejectEmptyFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.xlsx", XLSX_CONTENT_TYPE, new byte[0]);

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid Excel File"));
        }

        @Test
        @DisplayName("Should reject non-.xlsx file extension")
        void shouldRejectWrongExtension() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "data.csv", "text/csv", "a,b,c\n1,2,3".getBytes());

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid Excel File"))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString(".xlsx")));
        }

        @Test
        @DisplayName("Should reject corrupt file masquerading as .xlsx")
        void shouldRejectCorruptFile() throws Exception {
            byte[] randomBytes = "This is not an Excel file at all!".getBytes();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "fake.xlsx", XLSX_CONTENT_TYPE, randomBytes);

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Invalid Excel File"))
                    .andExpect(jsonPath("$.message").exists());
        }

        @Test
        @DisplayName("Should reject unsupported content type")
        void shouldRejectUnsupportedContentType() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.xlsx", "application/pdf", new byte[]{1, 2, 3});

            mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("Unsupported content type")));
        }

        @Test
        @DisplayName("Should not expose stack traces in error responses")
        void shouldNotExposeStackTraces() throws Exception {
            byte[] randomBytes = "garbage content".getBytes();

            MockMultipartFile file = new MockMultipartFile(
                    "file", "broken.xlsx", XLSX_CONTENT_TYPE, randomBytes);

            MvcResult result = mockMvc.perform(multipart(UPLOAD_URL).file(file))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("java.lang.");
            assertThat(body).doesNotContain("at org.");
            assertThat(body).doesNotContain("Exception");
        }
    }
}
