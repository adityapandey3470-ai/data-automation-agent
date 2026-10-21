package com.aditya.dataautomation.analysis;

import com.aditya.dataautomation.ai.model.DataProfileResponse;
import com.aditya.dataautomation.ai.service.DataInsightsService;
import com.aditya.dataautomation.analysis.model.ColumnStatistics;
import com.aditya.dataautomation.analysis.service.DataAnalysisService;
import com.aditya.dataautomation.analysis.service.impl.DataAnalysisServiceImpl;
import com.aditya.dataautomation.common.util.RowFingerprintUtil;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import com.aditya.dataautomation.excel.service.impl.ExcelFileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class Priority2DataIntegrityRegressionTest {

    private DataAnalysisService analysisService;
    private DataInsightsService insightsService;

    @BeforeEach
    void setUp() {
        analysisService = new DataAnalysisServiceImpl();
        insightsService = new DataInsightsService(analysisService);
    }

    // =========================================================================
    // FIX 5: Delimiter collisions & Null vs "null" fingerprinting
    // =========================================================================
    @Test
    @DisplayName("Fix 5: Delimiter-containing values and null vs 'null' never collide in fingerprint")
    void testFingerprintCollisionResistance() {
        List<String> cols = List.of("ColA", "ColB");

        // Collision attempt 1: Delimiter shift: "a|b" + "c" vs "a" + "b|c"
        Map<String, Object> r1 = Map.of("ColA", "a|b", "ColB", "c");
        Map<String, Object> r2 = Map.of("ColA", "a", "ColB", "b|c");
        String fp1 = RowFingerprintUtil.computeFingerprint(r1, cols);
        String fp2 = RowFingerprintUtil.computeFingerprint(r2, cols);
        assertThat(fp1).isNotEqualTo(fp2);

        // Collision attempt 2: Java null vs string "null"
        Map<String, Object> rNull = new HashMap<>();
        rNull.put("ColA", null);
        rNull.put("ColB", "val");

        Map<String, Object> rStrNull = new HashMap<>();
        rStrNull.put("ColA", "null");
        rStrNull.put("ColB", "val");

        String fpNull = RowFingerprintUtil.computeFingerprint(rNull, cols);
        String fpStrNull = RowFingerprintUtil.computeFingerprint(rStrNull, cols);
        assertThat(fpNull).isNotEqualTo(fpStrNull);

        // Collision attempt 3: Empty string vs null
        Map<String, Object> rEmpty = new HashMap<>();
        rEmpty.put("ColA", "");
        rEmpty.put("ColB", "val");
        String fpEmpty = RowFingerprintUtil.computeFingerprint(rEmpty, cols);
        assertThat(fpEmpty).isNotEqualTo(fpNull);

        // Verify duplicate detection in analysisService honors this
        List<Map<String, Object>> rows = List.of(rNull, rStrNull, rEmpty, rNull); // rNull appears twice
        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("TestDups")
                .headers(cols)
                .rows(rows)
                .build();

        List<Map<String, Object>> duplicates = analysisService.findDuplicatesByColumns(sheet, cols);
        // Only rNull has a true duplicate (2 rows returned)
        assertThat(duplicates).hasSize(2);
    }

    // =========================================================================
    // FIX 6: Consistent duplicate counts across <2,000 and >2,000 rows
    // =========================================================================
    @Test
    @DisplayName("Fix 6: Duplicate count is consistent for datasets across 2,000 boundary")
    void testDuplicateCountConsistencyAcrossThreshold() {
        // Build small dataset: 100 rows with exactly 5 duplicates
        List<Map<String, Object>> smallRows = new ArrayList<>();
        for (int i = 0; i < 95; i++) {
            smallRows.add(Map.of("ID", (long) i, "Val", "Data_" + i));
        }
        for (int i = 0; i < 5; i++) {
            smallRows.add(Map.of("ID", (long) i, "Val", "Data_" + i)); // repeat 5
        }
        ExcelSheetData smallSheet = ExcelSheetData.builder()
                .sheetName("Small")
                .headers(List.of("ID", "Val"))
                .rows(smallRows)
                .build();
        DataProfileResponse smallProfile = insightsService.generateProfile(smallSheet);
        assertThat(smallProfile.getDuplicateRowsCount()).isEqualTo(5);

        // Build large dataset: 2,500 rows with exactly 5 duplicates
        List<Map<String, Object>> largeRows = new ArrayList<>();
        for (int i = 0; i < 2495; i++) {
            largeRows.add(Map.of("ID", (long) i, "Val", "Data_" + i));
        }
        for (int i = 0; i < 5; i++) {
            largeRows.add(Map.of("ID", (long) i, "Val", "Data_" + i)); // repeat 5
        }
        ExcelSheetData largeSheet = ExcelSheetData.builder()
                .sheetName("Large")
                .headers(List.of("ID", "Val"))
                .rows(largeRows)
                .build();
        DataProfileResponse largeProfile = insightsService.generateProfile(largeSheet);
        assertThat(largeProfile.getDuplicateRowsCount()).isEqualTo(5);
    }

    // =========================================================================
    // FIX 7: Mixed numeric columns safely handled with non-numeric reporting
    // =========================================================================
    @Test
    @DisplayName("Fix 7: Mixed numeric columns compute valid statistics and report invalidCount")
    void testMixedNumericColumnsHandledSafely() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("Price", 100.0));
        rows.add(Map.of("Price", 200.0));
        rows.add(Map.of("Price", "300.0")); // String formatted number
        rows.add(Map.of("Price", "$400"));  // Currency formatted
        rows.add(Map.of("Price", "N/A"));   // Dirty invalid string
        rows.add(Map.of("Price", "--"));    // Dirty invalid string

        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Mixed")
                .headers(List.of("Price"))
                .rows(rows)
                .build();

        ColumnStatistics stats = analysisService.getColumnStatistics(sheet, "Price");
        assertThat(stats.getDataType()).isEqualTo("NUMERIC");
        assertThat(stats.getInvalidCount()).isEqualTo(2); // 2 invalid strings ignored

        // Valid values: 100 + 200 + 300 + 400 = 1000
        BigDecimal sum = (BigDecimal) stats.getSum();
        assertThat(sum).isEqualByComparingTo("1000");

        // Average: 1000 / 4 valid = 250.0000
        BigDecimal avg = (BigDecimal) stats.getAverage();
        assertThat(avg).isEqualByComparingTo("250");

        // Calculation via service methods doesn't crash on mixed values
        Object sumResult = analysisService.sum(sheet, "Price");
        assertThat(((BigDecimal) sumResult)).isEqualByComparingTo("1000");

        Object avgResult = analysisService.average(sheet, "Price");
        assertThat(((BigDecimal) avgResult)).isEqualByComparingTo("250");
    }

    // =========================================================================
    // FIX 8: Duplicate column headers normalized without losing data
    // =========================================================================
    @Test
    @DisplayName("Fix 8: Duplicate headers are disambiguated preserving all column data")
    void testNormalizeDuplicateHeaders() {
        List<String> rawHeaders = List.of("Amount", "Customer", "Amount", "Amount_1", "Amount");
        List<String> normalized = ExcelFileServiceImpl.normalizeHeaders(rawHeaders);

        // Expect:
        // 1st: Amount
        // 2nd: Customer
        // 3rd: Amount_1 collided with 4th, so resolved cleanly: Amount_2
        // 4th: Amount_1
        // 5th: Amount_3
        assertThat(normalized).hasSize(5);
        assertThat(normalized.get(0)).isEqualTo("Amount");
        assertThat(normalized.get(1)).isEqualTo("Customer");
        assertThat(new HashSet<>(normalized)).hasSize(5); // all unique!
        assertThat(normalized).contains("Amount", "Customer", "Amount_1");
    }

    // =========================================================================
    // FIX 9: Outlier calculation with identical values / zero variance
    // =========================================================================
    @Test
    @DisplayName("Fix 9: Outlier detection does not fail or misclassify identical values")
    void testOutliersZeroVarianceSafe() {
        // 100 rows with the exact same number (zero variance)
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(Map.of("Score", 50.0));
        }

        ExcelSheetData sheet = ExcelSheetData.builder()
                .sheetName("Uniform")
                .headers(List.of("Score"))
                .rows(rows)
                .build();

        DataProfileResponse profile = insightsService.generateProfile(sheet);
        assertThat(profile).isNotNull();

        // Ensure no false "Extreme numeric outlier" or "Statistical Outliers" generated
        boolean hasOutlierInsight = profile.getInsights().stream()
                .anyMatch(ins -> "OUTLIER".equalsIgnoreCase(ins.getCategory()));
        assertThat(hasOutlierInsight).isFalse();
    }
}
