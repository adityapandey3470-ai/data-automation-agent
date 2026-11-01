package com.aditya.dataautomation.dashboard.service;

import com.aditya.dataautomation.dashboard.exception.DashboardException;
import com.aditya.dataautomation.dashboard.model.*;
import com.aditya.dataautomation.dashboard.service.impl.DashboardServiceImpl;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.model.ExcelWorkbookData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DashboardService — no Spring context needed.
 */
class DashboardServiceTest {

    private DashboardService service;
    private ExcelWorkbookData workbook;

    @BeforeEach
    void setUp() {
        service = new DashboardServiceImpl();

        // Sales dataset
        List<String> headers = List.of("Date", "Product", "Region", "Quantity", "Revenue");
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("2026-09-01", "Laptop", "Delhi", 2L, 120000L));
        rows.add(row("2026-09-02", "Mouse", "Noida", 5L, 5000L));
        rows.add(row("2026-09-03", "Laptop", "Delhi", 1L, 60000L));
        rows.add(row("2026-09-04", "Keyboard", "Delhi", 3L, 9000L));
        rows.add(row("2026-09-05", "Mouse", "Noida", 2L, 2000L));

        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Sales")
                .headers(headers)
                .rows(rows)
                .build();

        workbook = ExcelWorkbookData.builder()
                .fileName("sales.xlsx")
                .sheets(List.of(sheet))
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
    // Dashboard creation
    // ========================================================================

    @Nested
    @DisplayName("Dashboard Creation")
    class Creation {

        @Test
        @DisplayName("Create dashboard successfully")
        void createDashboard() {
            Dashboard dashboard = service.createDashboard("Sales Dashboard", "Sales", workbook);

            assertThat(dashboard.getDashboardId()).isNotNull();
            assertThat(dashboard.getName()).isEqualTo("Sales Dashboard");
            assertThat(dashboard.getSheetName()).isEqualTo("Sales");
            assertThat(dashboard.getCreatedAt()).isNotNull();
            assertThat(dashboard.getWidgets()).isEmpty();
        }

        @Test
        @DisplayName("Create dashboard with invalid sheet throws exception")
        void invalidSheet() {
            assertThatThrownBy(() -> service.createDashboard("Test", "NonExistent", workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("Sheet not found");
        }

        @Test
        @DisplayName("Create dashboard with null workbook throws exception")
        void nullWorkbook() {
            assertThatThrownBy(() -> service.createDashboard("Test", "Sales", null))
                    .isInstanceOf(DashboardException.class);
        }

        @Test
        @DisplayName("Create dashboard with empty name throws exception")
        void emptyName() {
            assertThatThrownBy(() -> service.createDashboard("", "Sales", workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("findDashboard returns metadata and enforces ownership")
        void findDashboardOwnership() {
            Dashboard d = service.createDashboard(100L, "f1", "sales.xlsx", "User 100 Dash", "Sales", workbook);
            Dashboard found = service.findDashboard(100L, d.getDashboardId());
            assertThat(found).isNotNull();
            assertThat(found.getName()).isEqualTo("User 100 Dash");

            // Access by different user should throw
            assertThatThrownBy(() -> service.findDashboard(200L, d.getDashboardId()))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("access denied");
        }
    }

    // ========================================================================
    // KPI widgets
    // ========================================================================

    @Nested
    @DisplayName("KPI Widgets")
    class KpiWidgets {

        private String dashboardId;

        @BeforeEach
        void createDash() {
            dashboardId = service.createDashboard("KPI Test", "Sales", workbook).getDashboardId();
        }

        @Test
        @DisplayName("KPI COUNT — total rows")
        void kpiCount() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title("Total Records")
                    .aggregation("COUNT")
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            assertThat(added.getKpiValue()).isEqualTo(5L);
        }

        @Test
        @DisplayName("KPI SUM — total revenue")
        void kpiSum() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title("Total Revenue")
                    .columnName("Revenue")
                    .aggregation("SUM")
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            assertThat((BigDecimal) added.getKpiValue()).isEqualByComparingTo(new BigDecimal("196000"));
        }

        @Test
        @DisplayName("KPI AVERAGE — average revenue")
        void kpiAverage() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title("Avg Revenue")
                    .columnName("Revenue")
                    .aggregation("AVERAGE")
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            assertThat((BigDecimal) added.getKpiValue()).isEqualByComparingTo(new BigDecimal("39200"));
        }

        @Test
        @DisplayName("KPI MIN — minimum revenue")
        void kpiMin() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title("Min Revenue")
                    .columnName("Revenue")
                    .aggregation("MIN")
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            assertThat((BigDecimal) added.getKpiValue()).isEqualByComparingTo(new BigDecimal("2000"));
        }

        @Test
        @DisplayName("KPI MAX — maximum revenue")
        void kpiMax() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.KPI)
                    .title("Max Revenue")
                    .columnName("Revenue")
                    .aggregation("MAX")
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            assertThat((BigDecimal) added.getKpiValue()).isEqualByComparingTo(new BigDecimal("120000"));
        }
    }

    // ========================================================================
    // Table widget
    // ========================================================================

    @Nested
    @DisplayName("Table Widget")
    class TableWidgetTests {

        private String dashboardId;

        @BeforeEach
        void createDash() {
            dashboardId = service.createDashboard("Table Test", "Sales", workbook).getDashboardId();
        }

        @Test
        @DisplayName("Table widget with selected columns")
        void tableWidget() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.TABLE)
                    .title("Product Table")
                    .columns(List.of("Product", "Revenue"))
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            assertThat(added.getTableData()).hasSize(5);
            assertThat(added.getTableData().get(0)).containsOnlyKeys("Product", "Revenue");
            assertThat(added.getTableData().get(0).get("Product")).isEqualTo("Laptop");
        }
    }

    // ========================================================================
    // Chart widgets
    // ========================================================================

    @Nested
    @DisplayName("Chart Widgets")
    class ChartWidgets {

        private String dashboardId;

        @BeforeEach
        void createDash() {
            dashboardId = service.createDashboard("Chart Test", "Sales", workbook).getDashboardId();
        }

        @Test
        @DisplayName("BAR_CHART — Revenue by Region (SUM)")
        void barChart() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.BAR_CHART)
                    .title("Revenue by Region")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Region")
                            .valueColumn("Revenue")
                            .aggregation("SUM")
                            .build())
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            List<ChartConfig.ChartDataPoint> points = added.getChartConfig().getDataPoints();

            assertThat(points).hasSize(2);

            ChartConfig.ChartDataPoint delhi = points.stream()
                    .filter(p -> "Delhi".equals(p.getLabel())).findFirst().orElseThrow();
            assertThat(((Number) delhi.getValue()).doubleValue()).isEqualTo(189000.0);

            ChartConfig.ChartDataPoint noida = points.stream()
                    .filter(p -> "Noida".equals(p.getLabel())).findFirst().orElseThrow();
            assertThat(((Number) noida.getValue()).doubleValue()).isEqualTo(7000.0);
        }

        @Test
        @DisplayName("LINE_CHART — Revenue by Product (AVERAGE)")
        void lineChart() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.LINE_CHART)
                    .title("Avg Revenue by Product")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Product")
                            .valueColumn("Revenue")
                            .aggregation("AVERAGE")
                            .build())
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            List<ChartConfig.ChartDataPoint> points = added.getChartConfig().getDataPoints();

            assertThat(points).hasSize(3); // Laptop, Mouse, Keyboard
        }

        @Test
        @DisplayName("PIE_CHART — Count by Region")
        void pieChart() {
            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.PIE_CHART)
                    .title("Sales by Region")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Region")
                            .valueColumn("Revenue")
                            .aggregation("COUNT")
                            .build())
                    .build();

            DashboardWidget added = service.addWidget(dashboardId, widget, workbook);
            List<ChartConfig.ChartDataPoint> points = added.getChartConfig().getDataPoints();

            assertThat(points).hasSize(2);

            ChartConfig.ChartDataPoint delhi = points.stream()
                    .filter(p -> "Delhi".equals(p.getLabel())).findFirst().orElseThrow();
            assertThat(delhi.getValue()).isEqualTo(3L);
        }
    }

    // ========================================================================
    // Multiple widgets
    // ========================================================================

    @Nested
    @DisplayName("Multiple Widgets")
    class MultipleWidgets {

        @Test
        @DisplayName("Dashboard with multiple widgets")
        void multipleWidgets() {
            String dashboardId = service.createDashboard("Full Dashboard", "Sales", workbook).getDashboardId();

            // Add KPI
            service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.KPI).title("Total Revenue")
                    .columnName("Revenue").aggregation("SUM").build(), workbook);

            // Add Table
            service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.TABLE).title("Products")
                    .columns(List.of("Product", "Revenue")).build(), workbook);

            // Add Chart
            service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.BAR_CHART).title("By Region")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Region").valueColumn("Revenue")
                            .aggregation("SUM").build()).build(), workbook);

            Dashboard dashboard = service.getDashboard(dashboardId, workbook);
            assertThat(dashboard.getWidgets()).hasSize(3);
            assertThat(dashboard.getWidgets().get(0).getKpiValue()).isNotNull();
            assertThat(dashboard.getWidgets().get(1).getTableData()).isNotNull();
            assertThat(dashboard.getWidgets().get(2).getChartConfig().getDataPoints()).isNotNull();
        }
    }

    // ========================================================================
    // Widget removal
    // ========================================================================

    @Nested
    @DisplayName("Widget Removal")
    class Removal {

        @Test
        @DisplayName("Remove widget from dashboard")
        void removeWidget() {
            String dashboardId = service.createDashboard("Remove Test", "Sales", workbook).getDashboardId();

            DashboardWidget added = service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.KPI).title("Count")
                    .aggregation("COUNT").build(), workbook);

            assertThat(service.getDashboard(dashboardId, workbook).getWidgets()).hasSize(1);

            service.removeWidget(dashboardId, added.getWidgetId());

            assertThat(service.getDashboard(dashboardId, workbook).getWidgets()).isEmpty();
        }

        @Test
        @DisplayName("Remove non-existent widget throws exception")
        void removeNonExistent() {
            String dashboardId = service.createDashboard("Remove Test", "Sales", workbook).getDashboardId();

            assertThatThrownBy(() -> service.removeWidget(dashboardId, "fake-id"))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("Widget not found");
        }
    }

    // ========================================================================
    // Validation / Error handling
    // ========================================================================

    @Nested
    @DisplayName("Validation Errors")
    class Validation {

        private String dashboardId;

        @BeforeEach
        void createDash() {
            dashboardId = service.createDashboard("Validation Test", "Sales", workbook).getDashboardId();
        }

        @Test
        @DisplayName("Invalid column in KPI throws exception")
        void invalidColumn() {
            assertThatThrownBy(() -> service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.KPI).title("Bad")
                    .columnName("NonExistent").aggregation("SUM").build(), workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("Column not found");
        }

        @Test
        @DisplayName("Invalid aggregation throws exception")
        void invalidAggregation() {
            assertThatThrownBy(() -> service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.KPI).title("Bad")
                    .columnName("Revenue").aggregation("MEDIAN").build(), workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("Invalid aggregation");
        }

        @Test
        @DisplayName("Numeric aggregation on non-numeric column throws exception")
        void nonNumericAggregation() {
            assertThatThrownBy(() -> service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.KPI).title("Bad")
                    .columnName("Product").aggregation("SUM").build(), workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("numeric");
        }

        @Test
        @DisplayName("Chart without chartConfig throws exception")
        void chartMissingConfig() {
            assertThatThrownBy(() -> service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.BAR_CHART).title("Bad").build(), workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("chartConfig");
        }

        @Test
        @DisplayName("Table without columns throws exception")
        void tableMissingColumns() {
            assertThatThrownBy(() -> service.addWidget(dashboardId, DashboardWidget.builder()
                    .type(WidgetType.TABLE).title("Bad").build(), workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("column");
        }

        @Test
        @DisplayName("Non-existent dashboard throws exception")
        void nonExistentDashboard() {
            assertThatThrownBy(() -> service.getDashboard("fake-id", workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("Dashboard not found");
        }

        @Test
        @DisplayName("Widget type null throws exception")
        void nullWidgetType() {
            assertThatThrownBy(() -> service.addWidget(dashboardId, DashboardWidget.builder()
                    .title("No Type").build(), workbook))
                    .isInstanceOf(DashboardException.class)
                    .hasMessageContaining("type");
        }
    }

    @Nested
    @DisplayName("Auto-Generate Dashboard")
    class AutoGenerate {

        @Test
        @DisplayName("Auto-generate dashboard from Sales dataset creates KPIs, Area chart, Bar chart, Donut chart, and Table")
        void autoGenerateSalesDashboard() {
            Dashboard d = service.autoGenerateDashboard(101L, "sales-file", "sales.xlsx", "Executive Board", "Sales", workbook);

            assertThat(d).isNotNull();
            assertThat(d.getUserId()).isEqualTo(101L);
            assertThat(d.getFileId()).isEqualTo("sales-file");
            assertThat(d.getName()).isEqualTo("Executive Board");
            assertThat(d.getWidgets()).isNotEmpty();

            List<DashboardWidget> kpis = d.getWidgets().stream()
                    .filter(w -> w.getType() == WidgetType.KPI)
                    .toList();
            assertThat(kpis.size()).isGreaterThanOrEqualTo(2);
            assertThat(kpis.get(0).getTitle()).isEqualTo("Total Records");
            assertThat(kpis.get(0).getKpiValue()).isEqualTo(5L);

            DashboardWidget revKpi = kpis.stream()
                    .filter(w -> w.getColumnName() != null && w.getColumnName().equalsIgnoreCase("Revenue"))
                    .findFirst()
                    .orElse(null);
            assertThat(revKpi).isNotNull();
            assertThat((BigDecimal) revKpi.getKpiValue()).isEqualByComparingTo(new BigDecimal("196000"));

            boolean hasTrendChart = d.getWidgets().stream()
                    .anyMatch(w -> w.getType() == WidgetType.AREA_CHART);
            assertThat(hasTrendChart).isTrue();

            boolean hasBarChart = d.getWidgets().stream()
                    .anyMatch(w -> w.getType() == WidgetType.BAR_CHART);
            assertThat(hasBarChart).isTrue();

            DashboardWidget tableWidget = d.getWidgets().stream()
                    .filter(w -> w.getType() == WidgetType.TABLE)
                    .findFirst()
                    .orElse(null);
            assertThat(tableWidget).isNotNull();
            assertThat(tableWidget.getTableData()).hasSize(5);
        }

        @Test
        @DisplayName("Auto-generate dashboard on empty sheet creates placeholder KPI without throwing")
        void autoGenerateEmptySheet() {
            ExcelSheetData emptySheet = ExcelSheetData.builder()
                    .sheetName("Empty")
                    .headers(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .build();
            ExcelWorkbookData emptyWb = ExcelWorkbookData.builder()
                    .fileName("empty.xlsx")
                    .sheets(List.of(emptySheet))
                    .build();

            Dashboard d = service.autoGenerateDashboard(1L, "f0", "empty.xlsx", null, "Empty", emptyWb);

            assertThat(d).isNotNull();
            assertThat(d.getWidgets()).hasSize(1);
            assertThat(d.getWidgets().get(0).getType()).isEqualTo(WidgetType.KPI);
            assertThat(d.getWidgets().get(0).getKpiValue()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Auto-generate on dataset with ID columns does not select ID as numeric metric")
        void autoGenerateIgnoresIdColumns() {
            List<String> headers = List.of("order_id", "Customer", "Amount");
            List<Map<String, Object>> rows = List.of(
                    Map.of("order_id", 1001L, "Customer", "Alice", "Amount", 500L),
                    Map.of("order_id", 1002L, "Customer", "Bob", "Amount", 300L)
            );
            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("Orders")
                    .headers(headers)
                    .rows(rows)
                    .build();
            ExcelWorkbookData wb = ExcelWorkbookData.builder()
                    .fileName("orders.xlsx")
                    .sheets(List.of(sheet))
                    .build();

            Dashboard d = service.autoGenerateDashboard("Orders Dash", "Orders", wb);

            DashboardWidget amountKpi = d.getWidgets().stream()
                    .filter(w -> w.getType() == WidgetType.KPI && "Amount".equalsIgnoreCase(w.getColumnName()))
                    .findFirst()
                    .orElse(null);
            assertThat(amountKpi).isNotNull();
            assertThat((BigDecimal) amountKpi.getKpiValue()).isEqualByComparingTo(new BigDecimal("800"));

            boolean hasOrderIdKpi = d.getWidgets().stream()
                    .anyMatch(w -> w.getType() == WidgetType.KPI && "order_id".equalsIgnoreCase(w.getColumnName()));
            assertThat(hasOrderIdKpi).isFalse();
        }

        @Test
        @DisplayName("Manually add AREA_CHART and DONUT_CHART widgets and compute data")
        void addAreaAndDonutCharts() {
            Dashboard d = service.createDashboard("Visuals", "Sales", workbook);

            DashboardWidget area = service.addWidget(d.getDashboardId(), DashboardWidget.builder()
                    .type(WidgetType.AREA_CHART)
                    .title("Revenue Trend")
                    .sheetName("Sales")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Date")
                            .valueColumn("Revenue")
                            .aggregation("SUM")
                            .build())
                    .build(), workbook);

            assertThat(area.getChartConfig().getDataPoints()).hasSize(5);

            DashboardWidget donut = service.addWidget(d.getDashboardId(), DashboardWidget.builder()
                    .type(WidgetType.DONUT_CHART)
                    .title("Sales by Region")
                    .sheetName("Sales")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Region")
                            .valueColumn("Revenue")
                            .aggregation("SUM")
                            .build())
                    .build(), workbook);

            assertThat(donut.getChartConfig().getDataPoints()).hasSize(2);
        }
    }

    // ========================================================================
    // Dashboard Enhancements (Fix 1, Fix 2, Fix 3)
    // ========================================================================

    @Nested
    @DisplayName("Dashboard Enhancements")
    class DashboardEnhancements {

        @Test
        @DisplayName("Fix 1: Smart default aggregation chooses AVERAGE for UnitPrice with 'Average UnitPrice' title")
        void smartAggregationUnitPrice() {
            List<String> headers = List.of("Product", "UnitPrice", "Quantity");
            List<Map<String, Object>> rows = List.of(
                    Map.of("Product", "Shirt", "UnitPrice", 500.0, "Quantity", 2L),
                    Map.of("Product", "Pant", "UnitPrice", 1000.0, "Quantity", 1L)
            );
            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("Inventory")
                    .headers(headers)
                    .rows(rows)
                    .build();
            ExcelWorkbookData wb = ExcelWorkbookData.builder()
                    .fileName("inventory.xlsx")
                    .sheets(List.of(sheet))
                    .build();

            Dashboard d = service.autoGenerateDashboard("Inv Dash", "Inventory", wb);

            DashboardWidget unitPriceKpi = d.getWidgets().stream()
                    .filter(w -> w.getType() == WidgetType.KPI && "UnitPrice".equalsIgnoreCase(w.getColumnName()))
                    .findFirst()
                    .orElse(null);

            assertThat(unitPriceKpi).isNotNull();
            assertThat(unitPriceKpi.getAggregation()).isEqualTo("AVERAGE");
            assertThat(unitPriceKpi.getTitle()).isEqualTo("Average UnitPrice");
            assertThat((BigDecimal) unitPriceKpi.getKpiValue()).isEqualByComparingTo(new BigDecimal("750"));

            DashboardWidget quantityKpi = d.getWidgets().stream()
                    .filter(w -> w.getType() == WidgetType.KPI && "Quantity".equalsIgnoreCase(w.getColumnName()))
                    .findFirst()
                    .orElse(null);

            assertThat(quantityKpi).isNotNull();
            assertThat(quantityKpi.getAggregation()).isEqualTo("SUM");
            assertThat(quantityKpi.getTitle()).isEqualTo("Total Quantity");
            assertThat((BigDecimal) quantityKpi.getKpiValue()).isEqualByComparingTo(new BigDecimal("3"));
        }

        @Test
        @DisplayName("Fix 2: Case-insensitive category merging combines variants into dominant casing variant")
        void caseInsensitiveCategoryMerging() {
            List<String> headers = List.of("Category", "Sales");
            List<Map<String, Object>> rows = List.of(
                    Map.of("Category", "CLOTHING", "Sales", 100.0),
                    Map.of("Category", "Clothing", "Sales", 200.0),
                    Map.of("Category", "Clothing", "Sales", 150.0),
                    Map.of("Category", " clothing ", "Sales", 50.0),
                    Map.of("Category", "Electronics", "Sales", 500.0)
            );
            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("Products")
                    .headers(headers)
                    .rows(rows)
                    .build();
            ExcelWorkbookData wb = ExcelWorkbookData.builder()
                    .fileName("products.xlsx")
                    .sheets(List.of(sheet))
                    .build();

            Dashboard d = service.createDashboard("Merge Dash", "Products", wb);

            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.BAR_CHART)
                    .title("Sales by Category")
                    .sheetName("Products")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Category")
                            .valueColumn("Sales")
                            .aggregation("SUM")
                            .mergeSimilarCategories(true)
                            .build())
                    .build();

            DashboardWidget added = service.addWidget(d.getDashboardId(), widget, wb);
            List<ChartConfig.ChartDataPoint> points = added.getChartConfig().getDataPoints();

            assertThat(points).hasSize(2);

            ChartConfig.ChartDataPoint clothingPoint = points.stream()
                    .filter(p -> "Clothing".equals(p.getLabel()))
                    .findFirst()
                    .orElse(null);

            assertThat(clothingPoint).isNotNull();
            // Dominant casing was "Clothing" (frequency 2 vs 1 each for CLOTHING and ' clothing ')
            assertThat(clothingPoint.getLabel()).isEqualTo("Clothing");
            assertThat(((Number) clothingPoint.getValue()).doubleValue()).isEqualTo(500.0);

            ChartConfig.ChartDataPoint electPoint = points.stream()
                    .filter(p -> "Electronics".equals(p.getLabel()))
                    .findFirst()
                    .orElse(null);
            assertThat(electPoint).isNotNull();
            assertThat(((Number) electPoint.getValue()).doubleValue()).isEqualTo(500.0);
        }

        @Test
        @DisplayName("Fix 3: 15 categories produces top 10 categories + 1 'Other (5 categories)' bucket")
        void top10CategoriesAndOtherBucket() {
            List<String> headers = List.of("Category", "Amount");
            List<Map<String, Object>> rows = new ArrayList<>();
            // Cat01 through Cat15 with decreasing values
            for (int i = 1; i <= 15; i++) {
                rows.add(Map.of("Category", String.format("Cat%02d", i), "Amount", (double) (100 - i)));
            }

            ExcelSheetData sheet = ExcelSheetData.builder()
                    .sheetName("CategoriesSheet")
                    .headers(headers)
                    .rows(rows)
                    .build();
            ExcelWorkbookData wb = ExcelWorkbookData.builder()
                    .fileName("categories.xlsx")
                    .sheets(List.of(sheet))
                    .build();

            Dashboard d = service.createDashboard("Cats Dash", "CategoriesSheet", wb);

            DashboardWidget widget = DashboardWidget.builder()
                    .type(WidgetType.BAR_CHART)
                    .title("Top Categories")
                    .sheetName("CategoriesSheet")
                    .chartConfig(ChartConfig.builder()
                            .categoryColumn("Category")
                            .valueColumn("Amount")
                            .aggregation("SUM")
                            .build())
                    .build();

            DashboardWidget added = service.addWidget(d.getDashboardId(), widget, wb);
            List<ChartConfig.ChartDataPoint> points = added.getChartConfig().getDataPoints();

            // Total 11 points: 10 top points + 1 other bucket
            assertThat(points).hasSize(11);

            ChartConfig.ChartDataPoint otherPoint = points.get(10);
            assertThat(otherPoint.getLabel()).isEqualTo("Other (5 categories)");

            // Remainder is Cat11 (89) + Cat12 (88) + Cat13 (87) + Cat14 (86) + Cat15 (85) = 435.0
            double expectedRemainderSum = 89 + 88 + 87 + 86 + 85;
            assertThat(((Number) otherPoint.getValue()).doubleValue()).isEqualTo(expectedRemainderSum);
        }
    }
}
