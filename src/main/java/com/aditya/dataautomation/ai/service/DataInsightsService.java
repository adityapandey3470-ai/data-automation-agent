package com.aditya.dataautomation.ai.service;

import com.aditya.dataautomation.ai.model.DataInsight;
import com.aditya.dataautomation.ai.model.DataProfileResponse;
import com.aditya.dataautomation.ai.model.DataQualityReport;
import com.aditya.dataautomation.ai.model.QualityIssue;
import com.aditya.dataautomation.analysis.model.ColumnStatistics;
import com.aditya.dataautomation.analysis.service.DataAnalysisService;
import com.aditya.dataautomation.common.util.RowFingerprintUtil;
import com.aditya.dataautomation.excel.model.ExcelSheetData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataInsightsService {

    private final DataAnalysisService analysisService;
    private final Map<String, DataProfileResponse> profileCache = new ConcurrentHashMap<>();

    public DataProfileResponse generateProfile(ExcelSheetData sheet) {
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet data cannot be null");
        }

        int totalRows = sheet.getRowCount();
        int totalColumns = sheet.getColumnCount();
        long totalCells = (long) totalRows * totalColumns;
        List<String> headers = sheet.getHeaders() != null ? sheet.getHeaders() : Collections.emptyList();

        String cacheKey = sheet.getSheetName() + "#" + totalRows + "#" + totalColumns + "#"
                + (sheet.getRows() != null ? System.identityHashCode(sheet.getRows()) : 0);
        DataProfileResponse cached = profileCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, ColumnStatistics> statsMap = new ConcurrentHashMap<>();
        headers.parallelStream().forEach(col -> {
            try {
                ColumnStatistics stats = analysisService.getColumnStatistics(sheet, col);
                if (stats != null) {
                    statsMap.put(col, stats);
                }
            } catch (Exception e) {
                log.warn("Failed to compute stats for column: {}", col, e);
            }
        });

        Map<String, ColumnStatistics> orderedStatsMap = new LinkedHashMap<>();
        long totalMissingCells = 0;
        for (String col : headers) {
            ColumnStatistics stats = statsMap.get(col);
            if (stats != null) {
                orderedStatsMap.put(col, stats);
                totalMissingCells += stats.getNullCount();
            }
        }

        int duplicateCount = 0;
        try {
            if (sheet.getRows() != null && headers != null && !headers.isEmpty()) {
                Set<String> seen = new HashSet<>(Math.min(totalRows, 65536));
                for (Map<String, Object> r : sheet.getRows()) {
                    if (r != null) {
                        String fp = RowFingerprintUtil.computeFingerprint(r, headers);
                        if (!seen.add(fp)) {
                            duplicateCount++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to detect duplicates", e);
        }

        double duplicatePct = totalRows > 0
                ? BigDecimal.valueOf((double) duplicateCount / totalRows * 100)
                .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        double completeness = totalCells > 0
                ? BigDecimal.valueOf((double) (totalCells - totalMissingCells) / totalCells * 100)
                .setScale(1, RoundingMode.HALF_UP).doubleValue()
                : 100.0;

        List<DataInsight> insights = generateInsights(sheet, orderedStatsMap, duplicateCount, duplicatePct, completeness, totalMissingCells);

        List<String> suggestedQuestions = generateSuggestedQuestions(headers, orderedStatsMap, duplicateCount);

        DataQualityReport qualityReport = calculateQualityReport(sheet, orderedStatsMap, duplicateCount, duplicatePct, completeness, totalMissingCells);

        DataProfileResponse response = DataProfileResponse.builder()
                .sheetName(sheet.getSheetName())
                .totalRows(totalRows)
                .totalColumns(totalColumns)
                .totalCells(totalCells)
                .missingCells(totalMissingCells)
                .completenessPercentage(completeness)
                .duplicateRowsCount(duplicateCount)
                .duplicatePercentage(duplicatePct)
                .headers(headers)
                .columnStatistics(orderedStatsMap)
                .qualityReport(qualityReport)
                .insights(insights)
                .suggestedQuestions(suggestedQuestions)
                .build();

        if (profileCache.size() > 25) {
            profileCache.clear();
        }
        profileCache.put(cacheKey, response);

        return response;
    }

    private List<DataInsight> generateInsights(
            ExcelSheetData sheet,
            Map<String, ColumnStatistics> statsMap,
            int duplicateCount,
            double duplicatePct,
            double completeness,
            long totalMissingCells) {

        List<DataInsight> list = new ArrayList<>();
        int idSeq = 1;
        int totalRows = sheet.getRowCount();

        if (completeness < 95.0) {
            list.add(DataInsight.builder()
                    .id("INS-" + idSeq++)
                    .title("Data Completeness Alert")
                    .category("QUALITY")
                    .severity("WARNING")
                    .statisticalFact(String.format("Found %d missing cells across %d rows (%.1f%% overall completeness).", totalMissingCells, totalRows, completeness))
                    .interpretation("Missing records in key columns may distort analytical totals and downstream visualizations.")
                    .suggestedAction("Clean dataset using Drop Rows or Fill Strategies.")
                    .suggestedCommand("handle missing values")
                    .confidence(0.95)
                    .build());
        } else {
            list.add(DataInsight.builder()
                    .id("INS-" + idSeq++)
                    .title("High Data Hygiene")
                    .category("QUALITY")
                    .severity("SUCCESS")
                    .statisticalFact(String.format("Dataset has %.1f%% overall completeness with only %d missing cells.", completeness, totalMissingCells))
                    .interpretation("Data quality is robust for accurate aggregation and direct reporting.")
                    .confidence(0.98)
                    .build());
        }

        if (duplicateCount > 0) {
            list.add(DataInsight.builder()
                    .id("INS-" + idSeq++)
                    .title("Duplicate Records Detected")
                    .category("QUALITY")
                    .severity("WARNING")
                    .statisticalFact(String.format("Identified %d exact duplicate rows (%.1f%% of all records).", duplicateCount, duplicatePct))
                    .interpretation("Duplicate rows can artificially inflate metrics like Sum, Count, and Mean revenue.")
                    .suggestedAction("Deduplicate dataset keeping the first occurrence.")
                    .suggestedCommand("remove duplicates")
                    .confidence(1.0)
                    .build());
        }

        for (Map.Entry<String, ColumnStatistics> entry : statsMap.entrySet()) {
            String col = entry.getKey();
            ColumnStatistics stats = entry.getValue();

            if (stats.getNullCount() > 0 && totalRows > 0) {
                double colNullPct = (double) stats.getNullCount() / totalRows * 100.0;
                if (colNullPct > 20.0) {
                    list.add(DataInsight.builder()
                            .id("INS-" + idSeq++)
                            .title("Heavy Missingness in " + col)
                            .category("QUALITY")
                            .severity("WARNING")
                            .affectedColumn(col)
                            .statisticalFact(String.format("Column '%s' is missing in %d rows (%.1f%% of records).", col, stats.getNullCount(), colNullPct))
                            .interpretation("Field appears optional or inconsistently collected during data entry.")
                            .suggestedAction(String.format("Fill missing '%s' with a fallback value or default zero.", col))
                            .suggestedCommand(String.format("fill missing in %s with default", col))
                            .confidence(0.92)
                            .build());
                }
            }

            if ("NUMERIC".equalsIgnoreCase(stats.getDataType()) && stats.getCount() >= 4) {
                analyzeNumericColumn(col, stats, sheet, list, idSeq);
            }

            if ("STRING".equalsIgnoreCase(stats.getDataType()) && stats.getTopValues() != null && !stats.getTopValues().isEmpty()) {
                Map.Entry<String, Long> top = stats.getTopValues().get(0);
                if (stats.getCount() > 0) {
                    double dominancePct = (double) top.getValue() / stats.getCount() * 100.0;
                    if (dominancePct > 70.0 && stats.getDistinctCount() > 1) {
                        list.add(DataInsight.builder()
                                .id("INS-" + idSeq++)
                                .title("Category Concentration in " + col)
                                .category("DISTRIBUTION")
                                .severity("INFO")
                                .affectedColumn(col)
                                .statisticalFact(String.format("Value '%s' accounts for %d of %d records (%.1f%% share).", top.getKey(), top.getValue(), stats.getCount(), dominancePct))
                                .interpretation("High class imbalance observed. Most transactions originate from a single category.")
                                .suggestedAction(String.format("Filter by '%s' equals '%s' for focused sub-cohort analysis.", col, top.getKey()))
                                .suggestedCommand(String.format("filter %s equals %s", col, top.getKey()))
                                .confidence(0.90)
                                .build());
                    }
                }
            }
        }

        return list;
    }

    private void analyzeNumericColumn(
            String col,
            ColumnStatistics stats,
            ExcelSheetData sheet,
            List<DataInsight> list,
            int idSeq) {

        try {
            List<Map<String, Object>> rows = sheet.getRows();
            if (rows == null || rows.isEmpty()) return;

            double mean = stats.getAverage() != null ? ((Number) stats.getAverage()).doubleValue() : 0.0;
            if (Double.isNaN(mean) || Double.isInfinite(mean)) return;

            if (stats.getMin() != null && stats.getMax() != null) {
                double minVal = ((Number) stats.getMin()).doubleValue();
                double maxVal = ((Number) stats.getMax()).doubleValue();
                if (Math.abs(maxVal - minVal) < 1e-9) {
                    return;
                }
            }

            int rowCount = rows.size();
            int sampleLimit = Math.min(rowCount, 3000);
            int stride = Math.max(1, rowCount / sampleLimit);

            double varianceSum = 0.0;
            int count = 0;
            for (int i = 0; i < rowCount && count < sampleLimit; i += stride) {
                Object v = rows.get(i).get(col);
                if (v instanceof Number n) {
                    varianceSum += Math.pow(n.doubleValue() - mean, 2);
                    count++;
                }
            }

            if (count < 4) return;
            double stdDev = Math.sqrt(varianceSum / count);
            if (Double.isNaN(stdDev) || Double.isInfinite(stdDev) || stdDev < 1e-9) return;

            double threshold = 2.8 * stdDev;
            long outlierCount = 0;

            if (rowCount <= 3000) {
                for (Map<String, Object> r : rows) {
                    Object v = r.get(col);
                    if (v instanceof Number n) {
                        if (Math.abs(n.doubleValue() - mean) > threshold) {
                            outlierCount++;
                        }
                    }
                }
            } else {

                int sampleOutliers = 0;
                int validSample = 0;
                for (int i = 0; i < rowCount && validSample < sampleLimit; i += stride) {
                    Object v = rows.get(i).get(col);
                    if (v instanceof Number n) {
                        validSample++;
                        if (Math.abs(n.doubleValue() - mean) > threshold) {
                            sampleOutliers++;
                        }
                    }
                }
                if (validSample > 0) {
                    outlierCount = Math.round(((double) sampleOutliers / validSample) * stats.getCount());
                }
            }

            if (outlierCount > 0) {
                double maxVal = stats.getMax() != null ? ((Number) stats.getMax()).doubleValue() : 0;
                double minVal = stats.getMin() != null ? ((Number) stats.getMin()).doubleValue() : 0;

                list.add(DataInsight.builder()
                        .id("INS-" + idSeq)
                        .title("Statistical Outliers in " + col)
                        .category("OUTLIER")
                        .severity("WARNING")
                        .affectedColumn(col)
                        .statisticalFact(String.format("Found %d values beyond 2.8 standard deviations (Mean: %,.2f, StdDev: %,.2f, Min: %,.2f, Max: %,.2f).",
                                outlierCount, mean, stdDev, minVal, maxVal))
                        .interpretation("Extreme values may indicate VIP high-ticket transactions or potential data recording discrepancies.")
                        .suggestedAction(String.format("Inspect maximum transactions by sorting '%s' descending.", col))
                        .suggestedCommand(String.format("sort %s desc", col))
                        .confidence(0.88)
                        .build());
            }
        } catch (Exception e) {
            log.debug("Outlier detection skipped for {}", col, e);
        }
    }

    private List<String> generateSuggestedQuestions(
            List<String> headers,
            Map<String, ColumnStatistics> statsMap,
            int duplicateCount) {

        List<String> questions = new ArrayList<>();

        if (duplicateCount > 0) {
            questions.add("How many duplicate rows exist and should they be removed?");
        }

        String primaryNumeric = null;
        String primaryCategorical = null;

        for (Map.Entry<String, ColumnStatistics> entry : statsMap.entrySet()) {
            if (primaryNumeric == null && "NUMERIC".equalsIgnoreCase(entry.getValue().getDataType())) {
                primaryNumeric = entry.getKey();
            }
            if (primaryCategorical == null && "STRING".equalsIgnoreCase(entry.getValue().getDataType())
                    && entry.getValue().getDistinctCount() > 1 && entry.getValue().getDistinctCount() < 100) {
                primaryCategorical = entry.getKey();
            }
        }

        if (primaryNumeric != null) {
            questions.add(String.format("What is the average and distribution of %s?", primaryNumeric));
            questions.add(String.format("Sort top records by %s descending", primaryNumeric));
        }

        if (primaryNumeric != null && primaryCategorical != null) {
            questions.add(String.format("Show total %s grouped by %s", primaryNumeric, primaryCategorical));
        }

        questions.add("What are the key data hygiene issues in this dataset?");

        return questions;
    }

    private DataQualityReport calculateQualityReport(
            ExcelSheetData sheet,
            Map<String, ColumnStatistics> statsMap,
            int duplicateCount,
            double duplicatePct,
            double completeness,
            long totalMissingCells) {

        List<QualityIssue> issues = new ArrayList<>();
        int issueSeq = 1;
        int totalRows = sheet.getRowCount();

        int completenessScore = (int) Math.round(completeness);
        for (Map.Entry<String, ColumnStatistics> entry : statsMap.entrySet()) {
            long nulls = entry.getValue().getNullCount();
            if (nulls > 0) {
                double pct = totalRows > 0 ? (double) nulls / totalRows * 100 : 0;
                String severity = pct > 20 ? "HIGH" : (pct > 5 ? "MEDIUM" : "LOW");
                issues.add(QualityIssue.builder()
                        .id("ISSUE-" + (issueSeq++))
                        .dimension("COMPLETENESS")
                        .severity(severity)
                        .column(entry.getKey())
                        .title("Missing values in column: " + entry.getKey())
                        .description(String.format("%d missing values (%.1f%% of column)", nulls, pct))
                        .issueCount((int) nulls)
                        .impactPercentage(BigDecimal.valueOf(pct).setScale(1, RoundingMode.HALF_UP).doubleValue())
                        .recommendation("Fill missing cells with a constant, mean/mode, or drop empty rows")
                        .operation("HANDLE_MISSING_VALUES")
                        .build());
            }
        }

        int uniquenessScore = Math.max(0, 100 - (int) Math.round(duplicatePct * 2.5));
        if (duplicateCount > 0) {
            String severity = duplicatePct > 10 || duplicateCount > 20 ? "HIGH" : "MEDIUM";
            issues.add(QualityIssue.builder()
                    .id("ISSUE-" + (issueSeq++))
                    .dimension("UNIQUENESS")
                    .severity(severity)
                    .column(null)
                    .title("Duplicate records detected")
                    .description(String.format("%d duplicate rows (%.1f%% of dataset)", duplicateCount, duplicatePct))
                    .issueCount(duplicateCount)
                    .impactPercentage(duplicatePct)
                    .recommendation("Deduplicate dataset using Remove Duplicates operation")
                    .operation("REMOVE_DUPLICATES")
                    .build());
        }

        int untrimmedCountTotal = 0;
        int consistencyDeductions = 0;
        if (sheet.getRows() != null && !sheet.getRows().isEmpty()) {
            List<Map<String, Object>> rows = sheet.getRows();
            int rowCount = rows.size();
            boolean largeDataset = rowCount > 2000;
            int sampleLimit = largeDataset ? 1500 : rowCount;
            int stride = largeDataset ? Math.max(1, rowCount / sampleLimit) : 1;
            double sampleMultiplier = largeDataset ? ((double) rowCount / Math.min(sampleLimit, rowCount / stride)) : 1.0;

            for (String col : sheet.getHeaders()) {
                int colUntrimmedSample = 0;
                int checked = 0;
                for (int i = 0; i < rowCount && checked < sampleLimit; i += stride) {
                    checked++;
                    Object val = rows.get(i).get(col);
                    if (val instanceof String s) {
                        if (!s.isEmpty() && (!s.equals(s.trim()) || s.contains("  "))) {
                            colUntrimmedSample++;
                        }
                    }
                }
                int colUntrimmed = (int) Math.round(colUntrimmedSample * sampleMultiplier);
                if (colUntrimmed > 0) {
                    untrimmedCountTotal += colUntrimmed;
                    consistencyDeductions += Math.min(20, (int) Math.ceil((double) colUntrimmed / totalRows * 30));
                    issues.add(QualityIssue.builder()
                            .id("ISSUE-" + (issueSeq++))
                            .dimension("CONSISTENCY")
                            .severity(colUntrimmed > 10 ? "MEDIUM" : "LOW")
                            .column(col)
                            .title("Inconsistent whitespace in column: " + col)
                            .description(String.format("%d values contain untrimmed whitespace or irregular spacing", colUntrimmed))
                            .issueCount(colUntrimmed)
                            .impactPercentage(BigDecimal.valueOf((double) colUntrimmed / totalRows * 100).setScale(1, RoundingMode.HALF_UP).doubleValue())
                            .recommendation("Trim leading/trailing whitespace and normalize spaces")
                            .operation("TRIM")
                            .build());
                }
            }
        }
        int consistencyScore = Math.max(40, 100 - consistencyDeductions);

        int validityScore = 100;
        for (Map.Entry<String, ColumnStatistics> entry : statsMap.entrySet()) {
            if ("NUMERIC".equalsIgnoreCase(entry.getValue().getDataType())) {
                Object minObj = entry.getValue().getMin();
                Object maxObj = entry.getValue().getMax();
                Object avgObj = entry.getValue().getAverage();
                if (minObj instanceof Number minNum && maxObj instanceof Number maxNum && avgObj instanceof Number avgNum) {
                    double min = minNum.doubleValue();
                    double max = maxNum.doubleValue();
                    double avg = avgNum.doubleValue();
                    if (min >= 0 && max > 0 && max > avg * 15 && avg > 0) {
                        validityScore = Math.max(50, validityScore - 10);
                        issues.add(QualityIssue.builder()
                                .id("ISSUE-" + (issueSeq++))
                                .dimension("VALIDITY")
                                .severity("MEDIUM")
                                .column(entry.getKey())
                                .title("Extreme numeric outlier in column: " + entry.getKey())
                                .description(String.format("Max value (%.2f) is more than 15x the column average (%.2f)", max, avg))
                                .issueCount(1)
                                .impactPercentage(0.5)
                                .recommendation("Verify if extreme values are authentic data points or input anomalies")
                                .operation("CONVERT_TYPE")
                                .build());
                    }
                }
            }
        }

        int overallScore = (int) Math.round(
                completenessScore * 0.35 +
                uniquenessScore * 0.25 +
                consistencyScore * 0.20 +
                validityScore * 0.20
        );
        overallScore = Math.min(100, Math.max(0, overallScore));

        String grade;
        String status;
        if (overallScore >= 95) {
            grade = "A+";
            status = "EXCELLENT";
        } else if (overallScore >= 88) {
            grade = "A";
            status = "EXCELLENT";
        } else if (overallScore >= 75) {
            grade = "B";
            status = "GOOD";
        } else if (overallScore >= 60) {
            grade = "C";
            status = "FAIR";
        } else if (overallScore >= 45) {
            grade = "D";
            status = "NEEDS_ATTENTION";
        } else {
            grade = "F";
            status = "CRITICAL";
        }

        int high = (int) issues.stream().filter(i -> "HIGH".equals(i.getSeverity())).count();
        int med = (int) issues.stream().filter(i -> "MEDIUM".equals(i.getSeverity())).count();
        int low = (int) issues.stream().filter(i -> "LOW".equals(i.getSeverity())).count();

        String summary = String.format("Dataset scored %d/100 (Grade %s - %s) across %d rows and %d columns. %d issues detected (%d high priority).",
                overallScore, grade, status, totalRows, sheet.getColumnCount(), issues.size(), high);

        return DataQualityReport.builder()
                .overallScore(overallScore)
                .grade(grade)
                .status(status)
                .completenessScore(completenessScore)
                .uniquenessScore(uniquenessScore)
                .consistencyScore(consistencyScore)
                .validityScore(validityScore)
                .totalIssuesCount(issues.size())
                .highSeverityCount(high)
                .mediumSeverityCount(med)
                .lowSeverityCount(low)
                .summaryText(summary)
                .issues(issues)
                .build();
    }
}
