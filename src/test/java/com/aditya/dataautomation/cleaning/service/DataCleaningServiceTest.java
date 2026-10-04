package com.aditya.dataautomation.cleaning.service;

import com.aditya.dataautomation.cleaning.exception.CleaningException;
import com.aditya.dataautomation.cleaning.model.*;
import com.aditya.dataautomation.cleaning.service.impl.DataCleaningServiceImpl;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class DataCleaningServiceTest {

    private DataCleaningService service;
    private ExcelWorkbookData workbook;

    @BeforeEach
    void setUp() {
        service = new DataCleaningServiceImpl();

        // Build test data matching the requirements example:
        // Customer | Email | City | Revenue
        List<String> headers = List.of("Customer", "Email", "City", "Revenue");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("  Rahul  ", "rahul@gmail.com", " Delhi ", "12000"));
        rows.add(row("Rahul", "rahul@gmail.com", "Delhi", "12000"));
        rows.add(row(" Priya ", "priya@gmail.com", null, "15000"));
        rows.add(row(" Amit ", null, "Noida", "8000"));

        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Customers")
                .headers(headers)
                .rows(rows)
                .build();

        workbook = ExcelWorkbookData.builder()
                .fileName("test.xlsx")
                .sheets(List.of(sheet))
                .build();
    }

    private Map<String, Object> row(Object customer, Object email, Object city, Object revenue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Customer", customer);
        map.put("Email", email);
        map.put("City", city);
        map.put("Revenue", revenue);
        return map;
    }


    @Nested
    @DisplayName("Remove Duplicates")
    class RemoveDuplicates {

        @Test
        @DisplayName("1. Remove complete duplicate rows")
        void removeCompleteDuplicates() {
            // Rows 0 and 1 differ only by whitespace in Customer/City, so are NOT exact duplicates.
            // Create exact duplicates:
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row("Rahul", "rahul@gmail.com", "Delhi", "12000"));
            rows.add(row("Rahul", "rahul@gmail.com", "Delhi", "12000"));
            rows.add(row("Priya", "priya@gmail.com", "Noida", "15000"));
            ExcelWorkbookData wb = buildWorkbook(rows);

            CleaningResult result = service.execute(wb, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REMOVE_DUPLICATES)
                    .build());

            assertThat(result.getRowsAfter()).isEqualTo(2);
            assertThat(result.getRowsRemoved()).isEqualTo(1);
        }

        @Test
        @DisplayName("2. Remove duplicates using selected columns")
        void removeDuplicatesByColumns() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REMOVE_DUPLICATES)
                    .columns(List.of("Email"))
                    .build());


            assertThat(result.getRowsAfter()).isEqualTo(3);
            assertThat(result.getRowsRemoved()).isEqualTo(1);
        }

        @Test
        @DisplayName("3. KEEP_FIRST keeps the first occurrence")
        void keepFirst() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REMOVE_DUPLICATES)
                    .columns(List.of("Email"))
                    .duplicateStrategy(DuplicateStrategy.KEEP_FIRST)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            // First row's Customer is "  Rahul  " (with spaces)
            assertThat(cleaned.getRows().get(0).get("Customer")).isEqualTo("  Rahul  ");
        }

        @Test
        @DisplayName("4. KEEP_LAST keeps the last occurrence")
        void keepLast() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REMOVE_DUPLICATES)
                    .columns(List.of("Email"))
                    .duplicateStrategy(DuplicateStrategy.KEEP_LAST)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            // Last row with rahul@gmail.com has Customer "Rahul" (no spaces)
            assertThat(cleaned.getRows().get(0).get("Customer")).isEqualTo("Rahul");
        }
    }


    @Nested
    @DisplayName("Missing Value Handling")
    class MissingValues {

        @Test
        @DisplayName("5. DROP_ROW removes rows with missing values")
        void dropRow() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                    .columnName("City")
                    .missingValueStrategy(MissingValueStrategy.DROP_ROW)
                    .build());


            assertThat(result.getRowsAfter()).isEqualTo(3);
            assertThat(result.getRowsRemoved()).isEqualTo(1);
        }

        @Test
        @DisplayName("6. FILL_CONSTANT fills missing values with a constant")
        void fillConstant() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                    .columnName("City")
                    .missingValueStrategy(MissingValueStrategy.FILL_CONSTANT)
                    .value("Unknown")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");

            assertThat(cleaned.getRows().get(2).get("City")).isEqualTo("Unknown");
            assertThat(result.getCellsChanged()).isEqualTo(1);
        }

        @Test
        @DisplayName("7. FILL_NUMERIC_ZERO fills missing numeric values with 0")
        void fillNumericZero() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                    .columnName("Email")
                    .missingValueStrategy(MissingValueStrategy.FILL_NUMERIC_ZERO)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");

            assertThat(cleaned.getRows().get(3).get("Email")).isEqualTo(0L);
            assertThat(result.getCellsChanged()).isEqualTo(1);
        }

        @Test
        @DisplayName("8. FORWARD_FILL uses previous non-empty value")
        void forwardFill() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                    .columnName("City")
                    .missingValueStrategy(MissingValueStrategy.FORWARD_FILL)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");

            assertThat(cleaned.getRows().get(2).get("City")).isEqualTo("Delhi");
        }

        @Test
        @DisplayName("9. BACKWARD_FILL uses next non-empty value")
        void backwardFill() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                    .columnName("City")
                    .missingValueStrategy(MissingValueStrategy.BACKWARD_FILL)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            // Row 2 (Priya) had null City. Row 3 has "Noida" → backward filled
            assertThat(cleaned.getRows().get(2).get("City")).isEqualTo("Noida");
        }
    }


    @Nested
    @DisplayName("String Cleaning")
    class StringCleaning {

        @Test
        @DisplayName("10. TRIM removes leading/trailing whitespace")
        void trim() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.TRIM)
                    .columnName("Customer")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(0).get("Customer")).isEqualTo("Rahul");
            assertThat(cleaned.getRows().get(2).get("Customer")).isEqualTo("Priya");
            assertThat(cleaned.getRows().get(3).get("Customer")).isEqualTo("Amit");
        }

        @Test
        @DisplayName("11. NORMALIZE_WHITESPACE collapses internal whitespace")
        void normalizeWhitespace() {
            List<Map<String, Object>> rows = new ArrayList<>();
            rows.add(row("New    Delhi   Boy", "a@b.com", "City", "100"));
            ExcelWorkbookData wb = buildWorkbook(rows);

            CleaningResult result = service.execute(wb, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.NORMALIZE_WHITESPACE)
                    .columnName("Customer")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(0).get("Customer")).isEqualTo("New Delhi Boy");
        }

        @Test
        @DisplayName("12. LOWERCASE converts strings to lowercase")
        void lowercase() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.LOWERCASE)
                    .columnName("Customer")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(1).get("Customer")).isEqualTo("rahul");
        }

        @Test
        @DisplayName("13. UPPERCASE converts strings to uppercase")
        void uppercase() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.UPPERCASE)
                    .columnName("Customer")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(1).get("Customer")).isEqualTo("RAHUL");
        }

        @Test
        @DisplayName("14. REPLACE_VALUE replaces specific values")
        void replaceValue() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REPLACE_VALUE)
                    .columnName("City")
                    .oldValue("Delhi")
                    .newValue("New Delhi")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(1).get("City")).isEqualTo("New Delhi");
        }
    }



    @Nested
    @DisplayName("Column Operations")
    class ColumnOperations {

        @Test
        @DisplayName("15. Rename column")
        void renameColumn() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.RENAME_COLUMN)
                    .columnName("Customer")
                    .newValue("customer_name")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getHeaders()).contains("customer_name");
            assertThat(cleaned.getHeaders()).doesNotContain("Customer");
            // Data should be accessible under new name
            assertThat(cleaned.getRows().get(1).get("customer_name")).isEqualTo("Rahul");
        }

        @Test
        @DisplayName("16. Remove column")
        void removeColumn() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REMOVE_COLUMN)
                    .columnName("Revenue")
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getHeaders()).hasSize(3);
            assertThat(cleaned.getHeaders()).doesNotContain("Revenue");
            assertThat(cleaned.getRows().get(0).containsKey("Revenue")).isFalse();
        }

        @Test
        @DisplayName("17. Reorder columns")
        void reorderColumns() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REORDER_COLUMNS)
                    .columns(List.of("Revenue", "Email", "City", "Customer"))
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getHeaders()).containsExactly("Revenue", "Email", "City", "Customer");
        }
    }



    @Nested
    @DisplayName("Type Conversion")
    class TypeConversion {

        @Test
        @DisplayName("18. Valid STRING to INTEGER conversion")
        void validStringToInteger() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.CONVERT_TYPE)
                    .columnName("Revenue")
                    .targetType(TargetDataType.INTEGER)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(0).get("Revenue")).isEqualTo(12000L);
            assertThat(cleaned.getRows().get(2).get("Revenue")).isEqualTo(15000L);
        }

        @Test
        @DisplayName("18b. Valid STRING to DECIMAL conversion")
        void validStringToDecimal() {
            CleaningResult result = service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.CONVERT_TYPE)
                    .columnName("Revenue")
                    .targetType(TargetDataType.DECIMAL)
                    .build());

            ExcelSheetData cleaned = result.getCleanedWorkbook().getSheet("Customers");
            assertThat(cleaned.getRows().get(0).get("Revenue")).isInstanceOf(BigDecimal.class);
        }

        @Test
        @DisplayName("19. Invalid type conversion throws CleaningException")
        void invalidConversion() {
            assertThatThrownBy(() -> service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.CONVERT_TYPE)
                    .columnName("Customer")
                    .targetType(TargetDataType.INTEGER)
                    .build()))
                    .isInstanceOf(CleaningException.class)
                    .hasMessageContaining("Cannot convert");
        }
    }



    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("20. Invalid sheet throws CleaningException")
        void invalidSheet() {
            assertThatThrownBy(() -> service.execute(workbook, TransformationRequest.builder()
                    .sheetName("NonExistent")
                    .operation(CleaningOperation.TRIM)
                    .columnName("Customer")
                    .build()))
                    .isInstanceOf(CleaningException.class)
                    .hasMessageContaining("Sheet not found");
        }

        @Test
        @DisplayName("21. Invalid column throws CleaningException")
        void invalidColumn() {
            assertThatThrownBy(() -> service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.TRIM)
                    .columnName("NonExistent")
                    .build()))
                    .isInstanceOf(CleaningException.class)
                    .hasMessageContaining("Column not found");
        }

        @Test
        @DisplayName("22. Duplicate column name after rename throws CleaningException")
        void duplicateColumnAfterRename() {
            assertThatThrownBy(() -> service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.RENAME_COLUMN)
                    .columnName("Customer")
                    .newValue("Email")  // Email already exists
                    .build()))
                    .isInstanceOf(CleaningException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("Null workbook throws CleaningException")
        void nullWorkbook() {
            assertThatThrownBy(() -> service.execute(null, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.TRIM)
                    .columnName("Customer")
                    .build()))
                    .isInstanceOf(CleaningException.class)
                    .hasMessageContaining("must not be null");
        }

        @Test
        @DisplayName("Missing strategy for HANDLE_MISSING_VALUES throws")
        void missingStrategy() {
            assertThatThrownBy(() -> service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                    .columnName("City")
                    .build()))
                    .isInstanceOf(CleaningException.class)
                    .hasMessageContaining("missingValueStrategy is required");
        }
    }



    @Nested
    @DisplayName("Pipeline Execution")
    class Pipeline {

        @Test
        @DisplayName("23. Multiple operations executed sequentially")
        void multipleOperations() {
            List<TransformationRequest> pipeline = List.of(
                    // Step 1: TRIM Customer
                    TransformationRequest.builder()
                            .sheetName("Customers")
                            .operation(CleaningOperation.TRIM)
                            .columnName("Customer")
                            .build(),
                    // Step 2: Remove duplicates by Email
                    TransformationRequest.builder()
                            .sheetName("Customers")
                            .operation(CleaningOperation.REMOVE_DUPLICATES)
                            .columns(List.of("Email"))
                            .build(),
                    // Step 3: Fill missing City with "Unknown"
                    TransformationRequest.builder()
                            .sheetName("Customers")
                            .operation(CleaningOperation.HANDLE_MISSING_VALUES)
                            .columnName("City")
                            .missingValueStrategy(MissingValueStrategy.FILL_CONSTANT)
                            .value("Unknown")
                            .build()
            );

            List<CleaningResult> results = service.executePipeline(workbook, pipeline);

            assertThat(results).hasSize(3);

            // After step 1: TRIM
            assertThat(results.get(0).getCellsChanged()).isGreaterThan(0);

            // After step 2: Remove duplicates (rahul@gmail.com duplicate removed)
            assertThat(results.get(1).getRowsRemoved()).isEqualTo(1);
            assertThat(results.get(1).getRowsAfter()).isEqualTo(3);

            // After step 3: Fill missing City
            CleaningResult finalResult = results.get(2);
            ExcelSheetData finalSheet = finalResult.getCleanedWorkbook().getSheet("Customers");

            // Verify no null cities remain
            boolean hasNullCity = finalSheet.getRows().stream()
                    .anyMatch(r -> r.get("City") == null);
            assertThat(hasNullCity).isFalse();
        }
    }



    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("24. Original workbook is NOT modified after cleaning")
        void originalNotModified() {
            // Capture original state
            ExcelSheetData originalSheet = workbook.getSheet("Customers");
            int originalRowCount = originalSheet.getRowCount();
            Object originalCustomer0 = originalSheet.getRows().get(0).get("Customer");

            // Execute TRIM + REMOVE_DUPLICATES
            service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.TRIM)
                    .columnName("Customer")
                    .build());

            service.execute(workbook, TransformationRequest.builder()
                    .sheetName("Customers")
                    .operation(CleaningOperation.REMOVE_DUPLICATES)
                    .columns(List.of("Email"))
                    .build());

            // Verify original is unchanged
            ExcelSheetData afterSheet = workbook.getSheet("Customers");
            assertThat(afterSheet.getRowCount()).isEqualTo(originalRowCount);
            assertThat(afterSheet.getRows().get(0).get("Customer")).isEqualTo(originalCustomer0);
        }
    }



    private ExcelWorkbookData buildWorkbook(List<Map<String, Object>> rows) {
        List<String> headers = List.of("Customer", "Email", "City", "Revenue");
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Customers")
                .headers(headers)
                .rows(rows)
                .build();
        return ExcelWorkbookData.builder()
                .fileName("test.xlsx")
                .sheets(List.of(sheet))
                .build();
    }
}
