package com.aditya.dataautomation.analysis.service;

import com.aditya.dataautomation.analysis.exception.AnalysisException;
import com.aditya.dataautomation.analysis.model.*;
import com.aditya.dataautomation.analysis.service.impl.DataAnalysisServiceImpl;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DataAnalysisService — no Spring context needed.
 */
class DataAnalysisServiceTest {

    private DataAnalysisService service;
    private ExcelSheetData salesSheet;

    @BeforeEach
    void setUp() {
        service = new DataAnalysisServiceImpl();

        // Build the sample dataset from the requirements
        List<String> headers = List.of("Date", "Product", "Region", "Quantity", "Revenue");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("2026-09-01 00:00:00", "Laptop", "Delhi", 2L, 120000L));
        rows.add(row("2026-09-02 00:00:00", "Mouse", "Noida", 5L, 5000L));
        rows.add(row("2026-09-03 00:00:00", "Laptop", "Delhi", 1L, 60000L));
        rows.add(row("2026-09-04 00:00:00", "Keyboard", "Delhi", 3L, 9000L));
        rows.add(row("2026-09-05 00:00:00", "Mouse", "Noida", 2L, 2000L));

        salesSheet = ExcelSheetData.builder()
                .sheetName("Sales")
                .headers(headers)
                .rows(rows)
                .build();
    }

    private Map<String, Object> row(Object date, Object product, Object region,
                                     Object quantity, Object revenue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Date", date);
        map.put("Product", product);
        map.put("Region", region);
        map.put("Quantity", quantity);
        map.put("Revenue", revenue);
        return map;
    }

    // ========================================================================
    // 1-4: Basic column operations
    // ========================================================================

    @Nested
    @DisplayName("Basic column operations")
    class BasicOperations {

        @Test
        @DisplayName("1. countRows returns total data rows")
        void countRows() {
            assertThat(service.countRows(salesSheet)).isEqualTo(5);
        }

        @Test
        @DisplayName("2. countNulls returns 0 when no nulls")
        void countNulls_noNulls() {
            assertThat(service.countNulls(salesSheet, "Product")).isEqualTo(0);
        }

        @Test
        @DisplayName("2b. countNulls counts null values")
        void countNulls_withNulls() {
            // Add a row with null Product
            List<Map<String, Object>> rows = new ArrayList<>(salesSheet.getRows());
            Map<String, Object> nullRow = new LinkedHashMap<>();
            nullRow.put("Date", "2026-09-06 00:00:00");
            nullRow.put("Product", null);
            nullRow.put("Region", "Mumbai");
            nullRow.put("Quantity", 1L);
            nullRow.put("Revenue", 1000L);
            rows.add(nullRow);

            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("Sales")
                    .headers(salesSheet.getHeaders())
                    .rows(rows)
                    .build();

            assertThat(service.countNulls(sheet, "Product")).isEqualTo(1);
        }

        @Test
        @DisplayName("3. countNonEmpty returns non-null count")
        void countNonEmpty() {
            assertThat(service.countNonEmpty(salesSheet, "Revenue")).isEqualTo(5);
        }

        @Test
        @DisplayName("4. distinctValues returns unique non-null values")
        void distinctValues() {
            Set<Object> distinct = service.distinctValues(salesSheet, "Product");
            assertThat(distinct).containsExactlyInAnyOrder("Laptop", "Mouse", "Keyboard");
            assertThat(distinct).hasSize(3);
        }

        @Test
        @DisplayName("4b. distinctValues does not count null")
        void distinctValues_excludesNull() {
            List<Map<String, Object>> rows = new ArrayList<>(salesSheet.getRows());
            Map<String, Object> nullRow = new LinkedHashMap<>();
            nullRow.put("Date", null);
            nullRow.put("Product", null);
            nullRow.put("Region", null);
            nullRow.put("Quantity", null);
            nullRow.put("Revenue", null);
            rows.add(nullRow);

            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("Sales")
                    .headers(salesSheet.getHeaders())
                    .rows(rows)
                    .build();

            Set<Object> distinct = service.distinctValues(sheet, "Region");
            assertThat(distinct).containsExactlyInAnyOrder("Delhi", "Noida");
        }
    }

    // ========================================================================
    // 5-8: Numeric operations
    // ========================================================================

    @Nested
    @DisplayName("Numeric operations")
    class NumericOperations {

        @Test
        @DisplayName("5. SUM of Revenue")
        void sum() {
            Object result = service.sum(salesSheet, "Revenue");
            assertThat(result).isEqualTo(new BigDecimal("196000"));
        }

        @Test
        @DisplayName("6. AVERAGE of Revenue")
        void average() {
            Object result = service.average(salesSheet, "Revenue");
            assertThat(result).isEqualTo(new BigDecimal("39200.0000"));
        }

        @Test
        @DisplayName("7. MIN of Revenue")
        void min() {
            Object result = service.min(salesSheet, "Revenue");
            assertThat(result).isEqualTo(new BigDecimal("2000"));
        }

        @Test
        @DisplayName("8. MAX of Revenue")
        void max() {
            Object result = service.max(salesSheet, "Revenue");
            assertThat(result).isEqualTo(new BigDecimal("120000"));
        }

        @Test
        @DisplayName("Numeric operation on non-numeric column throws AnalysisException")
        void numericOnStringColumn() {
            assertThatThrownBy(() -> service.sum(salesSheet, "Product"))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("not a numeric column");
        }
    }

    // ========================================================================
    // 9-12: Filtering
    // ========================================================================

    @Nested
    @DisplayName("Filtering")
    class Filtering {

        @Test
        @DisplayName("9. String EQUALS filter")
        void stringEquals() {
            List<FilterCondition> conditions = List.of(
                    FilterCondition.builder()
                            .column("Region")
                            .operator(FilterOperator.EQUALS)
                            .value("Delhi")
                            .build());

            List<Map<String, Object>> result = service.filter(salesSheet, conditions);
            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(row ->
                    assertThat(row.get("Region")).isEqualTo("Delhi"));
        }

        @Test
        @DisplayName("10. Numeric GREATER_THAN filter")
        void numericGreaterThan() {
            List<FilterCondition> conditions = List.of(
                    FilterCondition.builder()
                            .column("Revenue")
                            .operator(FilterOperator.GREATER_THAN)
                            .value(50000L)
                            .build());

            List<Map<String, Object>> result = service.filter(salesSheet, conditions);
            assertThat(result).hasSize(2); // 120000, 60000
        }

        @Test
        @DisplayName("11. Date GREATER_THAN filter")
        void dateGreaterThan() {
            List<FilterCondition> conditions = List.of(
                    FilterCondition.builder()
                            .column("Date")
                            .operator(FilterOperator.GREATER_THAN)
                            .value("2026-09-03 00:00:00")
                            .build());

            List<Map<String, Object>> result = service.filter(salesSheet, conditions);
            assertThat(result).hasSize(2); // 09-04 and 09-05
        }

        @Test
        @DisplayName("12. IS_NULL filter")
        void isNullFilter() {
            List<Map<String, Object>> rows = new ArrayList<>(salesSheet.getRows());
            Map<String, Object> nullRow = new LinkedHashMap<>();
            nullRow.put("Date", null);
            nullRow.put("Product", null);
            nullRow.put("Region", null);
            nullRow.put("Quantity", null);
            nullRow.put("Revenue", null);
            rows.add(nullRow);

            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("Sales")
                    .headers(salesSheet.getHeaders())
                    .rows(rows)
                    .build();

            List<FilterCondition> conditions = List.of(
                    FilterCondition.builder()
                            .column("Product")
                            .operator(FilterOperator.IS_NULL)
                            .build());

            List<Map<String, Object>> result = service.filter(sheet, conditions);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("CONTAINS filter")
        void containsFilter() {
            List<FilterCondition> conditions = List.of(
                    FilterCondition.builder()
                            .column("Product")
                            .operator(FilterOperator.CONTAINS)
                            .value("ouse")
                            .build());

            List<Map<String, Object>> result = service.filter(salesSheet, conditions);
            assertThat(result).hasSize(2); // Mouse x2
        }

        @Test
        @DisplayName("Multiple filter conditions (AND)")
        void multipleFilters() {
            List<FilterCondition> conditions = List.of(
                    FilterCondition.builder()
                            .column("Region")
                            .operator(FilterOperator.EQUALS)
                            .value("Delhi")
                            .build(),
                    FilterCondition.builder()
                            .column("Revenue")
                            .operator(FilterOperator.GREATER_THAN)
                            .value(10000L)
                            .build());

            List<Map<String, Object>> result = service.filter(salesSheet, conditions);
            assertThat(result).hasSize(2); // Laptop 120000 and Laptop 60000
        }
    }

    // ========================================================================
    // 13-15: Sorting
    // ========================================================================

    @Nested
    @DisplayName("Sorting")
    class Sorting {

        @Test
        @DisplayName("13. Ascending sort by Revenue")
        void ascendingSort() {
            List<SortCondition> conditions = List.of(
                    SortCondition.builder().column("Revenue").direction(SortDirection.ASC).build());

            List<Map<String, Object>> result = service.sort(salesSheet, conditions);
            assertThat(result.get(0).get("Revenue")).isEqualTo(2000L);
            assertThat(result.get(4).get("Revenue")).isEqualTo(120000L);
        }

        @Test
        @DisplayName("14. Descending sort by Revenue")
        void descendingSort() {
            List<SortCondition> conditions = List.of(
                    SortCondition.builder().column("Revenue").direction(SortDirection.DESC).build());

            List<Map<String, Object>> result = service.sort(salesSheet, conditions);
            assertThat(result.get(0).get("Revenue")).isEqualTo(120000L);
            assertThat(result.get(4).get("Revenue")).isEqualTo(2000L);
        }

        @Test
        @DisplayName("15. Multi-column sort: Region ASC, Revenue DESC")
        void multiColumnSort() {
            List<SortCondition> conditions = List.of(
                    SortCondition.builder().column("Region").direction(SortDirection.ASC).build(),
                    SortCondition.builder().column("Revenue").direction(SortDirection.DESC).build());

            List<Map<String, Object>> result = service.sort(salesSheet, conditions);

            // Delhi first (sorted by Revenue DESC)
            assertThat(result.get(0).get("Region")).isEqualTo("Delhi");
            assertThat(result.get(0).get("Revenue")).isEqualTo(120000L);
            assertThat(result.get(1).get("Revenue")).isEqualTo(60000L);
            assertThat(result.get(2).get("Revenue")).isEqualTo(9000L);

            // Noida next
            assertThat(result.get(3).get("Region")).isEqualTo("Noida");
            assertThat(result.get(3).get("Revenue")).isEqualTo(5000L);
            assertThat(result.get(4).get("Revenue")).isEqualTo(2000L);
        }
    }

    // ========================================================================
    // 16-18: Group By
    // ========================================================================

    @Nested
    @DisplayName("Group By")
    class GroupByTests {

        @Test
        @DisplayName("16. GROUP BY Region + COUNT")
        void groupByCount() {
            GroupResult result = service.groupBy(salesSheet, "Region", null, AggregationFunction.COUNT);

            assertThat(result.getGroups()).hasSize(2);

            GroupResult.GroupEntry delhi = result.getGroups().stream()
                    .filter(g -> "Delhi".equals(g.getKey())).findFirst().orElseThrow();
            assertThat(delhi.getCount()).isEqualTo(3);
            assertThat(delhi.getAggregationValue()).isEqualTo(3L);

            GroupResult.GroupEntry noida = result.getGroups().stream()
                    .filter(g -> "Noida".equals(g.getKey())).findFirst().orElseThrow();
            assertThat(noida.getCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("17. GROUP BY Region + SUM(Revenue)")
        void groupBySum() {
            GroupResult result = service.groupBy(salesSheet, "Region", "Revenue", AggregationFunction.SUM);

            GroupResult.GroupEntry delhi = result.getGroups().stream()
                    .filter(g -> "Delhi".equals(g.getKey())).findFirst().orElseThrow();
            assertThat(delhi.getAggregationValue()).isEqualTo(new BigDecimal("189000"));

            GroupResult.GroupEntry noida = result.getGroups().stream()
                    .filter(g -> "Noida".equals(g.getKey())).findFirst().orElseThrow();
            assertThat(noida.getAggregationValue()).isEqualTo(new BigDecimal("7000"));
        }

        @Test
        @DisplayName("18. GROUP BY Region + AVERAGE(Revenue)")
        void groupByAverage() {
            GroupResult result = service.groupBy(salesSheet, "Region", "Revenue", AggregationFunction.AVERAGE);

            GroupResult.GroupEntry delhi = result.getGroups().stream()
                    .filter(g -> "Delhi".equals(g.getKey())).findFirst().orElseThrow();
            assertThat(delhi.getAggregationValue()).isEqualTo(new BigDecimal("63000.0000"));
        }
    }

    // ========================================================================
    // 19-20: Duplicate detection
    // ========================================================================

    @Nested
    @DisplayName("Duplicate detection")
    class DuplicateDetection {

        @Test
        @DisplayName("19. findDuplicateRows — no exact duplicates in sample data")
        void noDuplicateRows() {
            List<Map<String, Object>> dups = service.findDuplicateRows(salesSheet);
            assertThat(dups).isEmpty();
        }

        @Test
        @DisplayName("20. findDuplicatesByColumns — duplicates by Product")
        void duplicatesByProduct() {
            List<Map<String, Object>> dups = service.findDuplicatesByColumns(
                    salesSheet, List.of("Product"));

            // Laptop appears 2 times, Mouse appears 2 times = 4 duplicate rows
            assertThat(dups).hasSize(4);
        }

        @Test
        @DisplayName("20b. findDuplicatesByColumns — duplicates by Region")
        void duplicatesByRegion() {
            List<Map<String, Object>> dups = service.findDuplicatesByColumns(
                    salesSheet, List.of("Region"));

            // Delhi: 3 rows, Noida: 2 rows — all 5 are duplicates
            assertThat(dups).hasSize(5);
        }
    }

    // ========================================================================
    // 21: Column Statistics
    // ========================================================================

    @Nested
    @DisplayName("Column statistics")
    class ColumnStats {

        @Test
        @DisplayName("21. Numeric column statistics")
        void numericColumnStats() {
            ColumnStatistics stats = service.getColumnStatistics(salesSheet, "Revenue");

            assertThat(stats.getColumn()).isEqualTo("Revenue");
            assertThat(stats.getDataType()).isEqualTo("NUMERIC");
            assertThat(stats.getCount()).isEqualTo(5);
            assertThat(stats.getNullCount()).isEqualTo(0);
            assertThat(stats.getDistinctCount()).isEqualTo(5);
            assertThat(stats.getSum()).isEqualTo(new BigDecimal("196000"));
            assertThat(stats.getMin()).isEqualTo(new BigDecimal("2000"));
            assertThat(stats.getMax()).isEqualTo(new BigDecimal("120000"));
        }

        @Test
        @DisplayName("21b. String column statistics")
        void stringColumnStats() {
            ColumnStatistics stats = service.getColumnStatistics(salesSheet, "Product");

            assertThat(stats.getColumn()).isEqualTo("Product");
            assertThat(stats.getDataType()).isEqualTo("STRING");
            assertThat(stats.getCount()).isEqualTo(5);
            assertThat(stats.getDistinctCount()).isEqualTo(3);
            assertThat(stats.getTopValues()).isNotNull();
            // Most frequent: Laptop(2), Mouse(2), Keyboard(1)
            assertThat(stats.getTopValues().get(0).getValue()).isEqualTo(2L);
        }

        @Test
        @DisplayName("21c. Date column statistics")
        void dateColumnStats() {
            ColumnStatistics stats = service.getColumnStatistics(salesSheet, "Date");

            assertThat(stats.getColumn()).isEqualTo("Date");
            assertThat(stats.getDataType()).isEqualTo("DATE");
            assertThat(stats.getMin()).isEqualTo("2026-09-01 00:00:00");
            assertThat(stats.getMax()).isEqualTo("2026-09-05 00:00:00");
        }
    }

    // ========================================================================
    // 22-24: Error handling
    // ========================================================================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("22. Missing sheet throws AnalysisException")
        void missingSheet() {
            assertThatThrownBy(() -> service.countRows(null))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("Sheet not found");
        }

        @Test
        @DisplayName("23. Missing column throws AnalysisException")
        void missingColumn() {
            assertThatThrownBy(() -> service.countNulls(salesSheet, "NonExistent"))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("Column not found");
        }

        @Test
        @DisplayName("24. Invalid operation (numeric on string) throws AnalysisException")
        void invalidOperation() {
            assertThatThrownBy(() -> service.sum(salesSheet, "Region"))
                    .isInstanceOf(AnalysisException.class)
                    .hasMessageContaining("not a numeric column");
        }

        @Test
        @DisplayName("Empty column list for duplicate detection throws AnalysisException")
        void emptyColumnsForDuplicates() {
            assertThatThrownBy(() -> service.findDuplicatesByColumns(salesSheet, List.of()))
                    .isInstanceOf(AnalysisException.class);
        }
    }
}
