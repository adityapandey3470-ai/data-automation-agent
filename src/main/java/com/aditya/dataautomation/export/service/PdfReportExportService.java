package com.aditya.dataautomation.export.service;

import com.aditya.dataautomation.ai.model.DataInsight;
import com.aditya.dataautomation.ai.model.DataProfileResponse;
import com.aditya.dataautomation.ai.model.DataQualityReport;
import com.aditya.dataautomation.ai.model.QualityIssue;
import com.aditya.dataautomation.ai.service.DataInsightsService;
import com.aditya.dataautomation.analysis.model.ColumnStatistics;
import com.aditya.dataautomation.model.excel.ExcelSheetData;
import com.aditya.dataautomation.model.excel.ExcelWorkbookData;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportExportService {

    private final DataInsightsService dataInsightsService;

    private static final Color PRIMARY_COLOR = new Color(30, 58, 138);     // Indigo/Navy #1e3a8a
    private static final Color ACCENT_COLOR = new Color(59, 130, 246);     // Blue #3b82f6
    private static final Color TEXT_COLOR = new Color(31, 41, 55);         // Slate #1f2937
    private static final Color MUTED_COLOR = new Color(107, 114, 128);     // Gray #6b7280
    private static final Color BG_LIGHT = new Color(248, 250, 252);        // Slate light
    private static final Color BORDER_COLOR = new Color(226, 232, 240);    // Slate border

    public byte[] generateExecutivePdfReport(ExcelWorkbookData workbook, String sheetName) {
        if (workbook == null || workbook.getSheets() == null || workbook.getSheets().isEmpty()) {
            throw new IllegalArgumentException("Workbook has no sheets to report on");
        }

        ExcelSheetData sheet = sheetName != null && !sheetName.isBlank()
                ? workbook.getSheet(sheetName)
                : workbook.getSheets().get(0);

        if (sheet == null) {
            sheet = workbook.getSheets().get(0);
        }

        DataProfileResponse profile = dataInsightsService.generateProfile(sheet);
        DataQualityReport quality = profile.getQualityReport();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header Banner
            addHeaderBanner(document, workbook.getFileName(), sheet.getSheetName());

            // 2. Executive KPI Summary Cards
            addKpiSummary(document, profile);

            // 3. AI Data Quality Score & Dimensions
            if (quality != null) {
                addDataQualitySection(document, quality);
            }

            // 4. Quality Issues & Actionable Recommendations
            if (quality != null && quality.getIssues() != null && !quality.getIssues().isEmpty()) {
                addQualityIssuesTable(document, quality);
            }

            // 5. Automated Data Insights
            if (profile.getInsights() != null && !profile.getInsights().isEmpty()) {
                addInsightsSection(document, profile);
            }

            // 6. Column Profile Summary
            if (profile.getColumnStatistics() != null && !profile.getColumnStatistics().isEmpty()) {
                addColumnStatisticsTable(document, profile.getColumnStatistics());
            }

            // 7. Footer
            addReportFooter(document);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF analysis report", e);
            throw new RuntimeException("Failed to generate PDF analysis report: " + e.getMessage(), e);
        }
    }

    private void addHeaderBanner(Document doc, String fileName, String sheetName) throws DocumentException {
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, PRIMARY_COLOR);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, ACCENT_COLOR);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED_COLOR);

        doc.add(new Paragraph("TABUFINERY DATA AUTOMATION", brandFont));
        doc.add(new Paragraph("Executive Data Quality & Analytics Report", titleFont));

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Paragraph meta = new Paragraph(String.format("File: %s | Sheet: %s | Generated: %s",
                fileName != null ? fileName : "Workbook", sheetName, dateStr), metaFont);
        meta.setSpacingAfter(15);
        doc.add(meta);
    }

    private void addKpiSummary(Document doc, DataProfileResponse profile) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addKpiCell(table, "Total Records", String.format("%,d", profile.getTotalRows()));
        addKpiCell(table, "Total Columns", String.valueOf(profile.getTotalColumns()));
        addKpiCell(table, "Completeness", String.format("%.1f%%", profile.getCompletenessPercentage()));
        addKpiCell(table, "Duplicate Rows", String.format("%,d (%.1f%%)", profile.getDuplicateRowsCount(), profile.getDuplicatePercentage()));

        doc.add(table);
    }

    private void addKpiCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(8);

        Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PRIMARY_COLOR);
        Font lblFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED_COLOR);

        cell.addElement(new Paragraph(value, valFont));
        cell.addElement(new Paragraph(label, lblFont));
        table.addCell(cell);
    }

    private void addDataQualitySection(Document doc, DataQualityReport quality) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
        Paragraph sectionTitle = new Paragraph("AI Data Quality Score & Health Dimensions", sectionFont);
        sectionTitle.setSpacingAfter(8);
        doc.add(sectionTitle);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addQualityScoreCell(table, "Overall Score", quality.getOverallScore() + "/100", "Grade " + quality.getGrade());
        addQualityScoreCell(table, "Completeness", quality.getCompletenessScore() + "/100", quality.getCompletenessScore() >= 90 ? "Healthy" : "Check Nulls");
        addQualityScoreCell(table, "Uniqueness", quality.getUniquenessScore() + "/100", quality.getUniquenessScore() >= 90 ? "Healthy" : "Duplicates");
        addQualityScoreCell(table, "Consistency", quality.getConsistencyScore() + "/100", quality.getConsistencyScore() >= 90 ? "Consistent" : "Whitespace");
        addQualityScoreCell(table, "Validity", quality.getValidityScore() + "/100", quality.getValidityScore() >= 90 ? "Valid" : "Outliers");

        doc.add(table);
    }

    private void addQualityScoreCell(PdfPTable table, String title, String score, String subtext) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(8);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_COLOR);
        Font scoreFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED_COLOR);

        cell.addElement(new Paragraph(title, titleFont));
        cell.addElement(new Paragraph(score, scoreFont));
        cell.addElement(new Paragraph(subtext, subFont));
        table.addCell(cell);
    }

    private void addQualityIssuesTable(Document doc, DataQualityReport quality) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
        Paragraph p = new Paragraph(String.format("Detected Issues & Actionable Fixes (%d total)", quality.getTotalIssuesCount()), sectionFont);
        p.setSpacingAfter(8);
        doc.add(p);

        PdfPTable table = new PdfPTable(new float[]{1.5f, 1.2f, 2.5f, 3.5f, 2.0f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableHeader(table, "Dimension", "Severity", "Target", "Issue Description", "Actionable Fix");

        Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_COLOR);
        int count = 0;
        for (QualityIssue issue : quality.getIssues()) {
            if (count++ >= 8) break; // Keep top 8 issues to avoid overflowing
            table.addCell(createCell(issue.getDimension(), rowFont));
            table.addCell(createCell(issue.getSeverity(), rowFont));
            table.addCell(createCell(issue.getColumn() != null ? issue.getColumn() : "Dataset", rowFont));
            table.addCell(createCell(issue.getDescription(), rowFont));
            table.addCell(createCell(issue.getOperation() != null ? issue.getOperation() : "Review", rowFont));
        }

        doc.add(table);
    }

    private void addInsightsSection(Document doc, DataProfileResponse profile) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
        Paragraph p = new Paragraph("Key Automated Insights & Statistical Findings", sectionFont);
        p.setSpacingAfter(8);
        doc.add(p);

        Font bFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, TEXT_COLOR);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED_COLOR);

        int count = 0;
        for (DataInsight insight : profile.getInsights()) {
            if (count++ >= 5) break;
            Paragraph item = new Paragraph();
            item.add(new Phrase("• " + insight.getTitle() + " (" + (insight.getCategory() != null ? insight.getCategory() : "INSIGHT") + "): ", bFont));
            String desc = insight.getStatisticalFact() != null ? insight.getStatisticalFact() : (insight.getInterpretation() != null ? insight.getInterpretation() : "");
            item.add(new Phrase(desc, bodyFont));
            item.setSpacingAfter(4);
            doc.add(item);
        }

        Paragraph spacer = new Paragraph();
        spacer.setSpacingAfter(10);
        doc.add(spacer);
    }

    private void addColumnStatisticsTable(Document doc, Map<String, ColumnStatistics> stats) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, PRIMARY_COLOR);
        Paragraph p = new Paragraph("Column Metrics Summary", sectionFont);
        p.setSpacingAfter(8);
        doc.add(p);

        PdfPTable table = new PdfPTable(new float[]{2.5f, 1.5f, 1.5f, 1.5f, 2.0f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(15);

        addTableHeader(table, "Column Name", "Data Type", "Count", "Null Count", "Distinct Values");

        Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_COLOR);
        int colLimit = 0;
        for (Map.Entry<String, ColumnStatistics> entry : stats.entrySet()) {
            if (colLimit++ >= 15) break;
            ColumnStatistics cs = entry.getValue();
            table.addCell(createCell(entry.getKey(), rowFont));
            table.addCell(createCell(cs.getDataType(), rowFont));
            table.addCell(createCell(String.valueOf(cs.getCount()), rowFont));
            table.addCell(createCell(String.valueOf(cs.getNullCount()), rowFont));
            table.addCell(createCell(String.valueOf(cs.getDistinctCount()), rowFont));
        }

        doc.add(table);
    }

    private void addReportFooter(Document doc) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, MUTED_COLOR);
        Paragraph footer = new Paragraph("Generated automatically by Tabufinery AI Data Automation Platform. Confidential.", footerFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
            cell.setBackgroundColor(PRIMARY_COLOR);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setPadding(4);
        cell.setBorderColor(BORDER_COLOR);
        return cell;
    }
}
