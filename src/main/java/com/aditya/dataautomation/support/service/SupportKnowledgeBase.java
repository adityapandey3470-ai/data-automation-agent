package com.aditya.dataautomation.support.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class SupportKnowledgeBase {

    public static final List<String> QUICK_QUESTIONS = List.of(
            "How do I upload an Excel file?",
            "How does Data Change Preview and Undo/Redo work?",
            "What data cleaning transformations are supported?",
            "How can I generate and export executive PDF reports?",
            "How do I save and resume projects?",
            "I'm encountering an error during file processing."
    );

    public static final String APP_DOCUMENTATION = """
            Tabufinery SaaS Technical Knowledge Base & Capabilities:
            1. Overview:
               - Tabufinery is an enterprise tabular data refinery and automated analytics platform.
               - Built with Spring Boot 3 + Apache POI backend and React 18 frontend.
               - Features automatic system dark/light theme switching.

            2. File Upload & Ingestion:
               - Supported formats: Modern Excel (.xlsx).
               - Maximum upload file size: 10 MB.
               - Multiple sheets in a single workbook are fully supported; switch sheets using the sheet selector tab bar.
               - If an upload fails, check: Is the file a valid .xlsx? Is it under 10MB? Is it password-protected (encrypted files cannot be opened)?

            3. Data Workspace & Data Cleaning Operations:
               - Interactive grid preview showing up to 500 rows per sheet.
               - Clean Missing Values: Fill missing/null cells with Mean, Median, Mode, Zero, or a Custom String/Number.
               - Drop Rows / Columns: Drop rows or columns that have missing/empty cells.
               - Deduplication: Remove identical rows across the sheet or selected subset.
               - Text Transformations: Trim leading/trailing whitespace, UPPERCASE, lowercase, Title Case.
               - Column Management: Rename column headers, change column data types (Text, Number, Date, Boolean), drop selected columns.
               - Value Replacement: Find and replace specific values across columns.

            4. Change Preview & Undo/Redo:
               - Before applying data cleaning or transformations, Tabufinery calculates a before/after diff preview showing affected rows and columns.
               - You must review and click "Apply" to commit the transformation, or "Cancel" to discard it.
               - Once applied, Tabufinery maintains a session history stack of up to 25 operations.
               - You can click "Undo" to revert to the previous state, or "Redo" to re-apply an undone action.
               - Undo/Redo state is tracked per user session.

            5. AI Data Analyst vs. AI Support Chatbot:
               - AI Data Analyst (in "AI Analyst & Insights" page): Analyzes your active spreadsheet workbook numbers, summary stats, distributions, and answers analytical questions about your specific data.
               - AI Support Chatbot (this floating widget): Helps you use the platform, troubleshoot errors, navigate features, explain workflows, and open support tickets. It does NOT expose private raw spreadsheet cells of other users.

            6. Analytics & Visualizations:
               - "Analytics & Charts" page generates automatic bar charts, line graphs, scatter plots, distributions, and summary metric cards (Sum, Average, Min, Max, Count, Distinct).
               - Filter, group, and sort tabular data in real-time.

            7. Export Hub:
               - Export Cleaned Excel (.xlsx): Downloads the refined workbook including all current transformations.
               - Export CSV (.csv): Downloads the active sheet as a standard UTF-8 comma-separated file.
               - Executive PDF Report: Compiles key metrics, data quality scores, completeness indicators, and column statistics into a downloadable PDF summary report.

            8. Saved Projects & Persistence:
               - Click "Projects" in the top navigation bar or Data Workspace.
               - Click "Save Current Project" to store the active workbook and session state in the persistent database under your user account.
               - Access your saved projects at any time to reopen, rename, or delete them.

            9. Authentication & Security:
               - Mandatory authentication: All workspace features, file uploads, and project data require signing up or logging in.
               - Password Reset: Click "Forgot Password?" on the Sign In modal to request a secure password reset token.
               - User data isolation: Every user has an isolated session and can only access their own saved projects.
               - Admin Role: Users with ROLE_ADMIN have access to the Admin Console to view user statistics and toggle account status.

            10. Official Support Escalation:
               - Official engineering email: support0912@gmail.com
               - You can submit a support ticket directly through this support widget by clicking "Submit Ticket".
            """;

    public String findDeterministicAnswer(String question) {
        if (question == null || question.isBlank()) {
            return "Hello! I am the Tabufinery Support Assistant. How can I help you refine, clean, or export your spreadsheet data today?";
        }

        String q = question.toLowerCase(Locale.ROOT).trim();

        if (q.contains("upload") || q.contains("import") || q.contains("file format") || q.contains("file size") || q.contains(".xlsx") || q.contains("csv upload")) {
            return "### Uploading Data to Tabufinery\n\n" +
                    "- **Supported Format**: Modern Excel files (`.xlsx`).\n" +
                    "- **File Size Limit**: Up to **10 MB** per workbook.\n" +
                    "- **Multi-sheet Support**: Workbooks with multiple tabs are automatically parsed. You can switch between sheets in the top sheet selector bar.\n" +
                    "- **How to Upload**: Navigate to the **Data Workspace** page or click **Upload Dataset** on the Overview screen. Drag & drop your `.xlsx` file into the upload zone or browse your local files.\n\n" +
                    "*Tip: Make sure your file is not password-encrypted before uploading.*";
        }

        if (q.contains("undo") || q.contains("redo") || q.contains("preview") || q.contains("history") || q.contains("revert")) {
            return "### Data Change Preview & Undo/Redo\n\n" +
                    "Tabufinery includes a non-destructive workflow for all transformations:\n\n" +
                    "1. **Before/After Preview**: Whenever you select a data cleaning operation (e.g., fill nulls or deduplicate), Tabufinery computes a live preview showing affected rows and columns.\n" +
                    "2. **Apply or Cancel**: The changes are only saved when you click **Apply**. Clicking **Cancel** discards the preview without touching your data.\n" +
                    "3. **Undo/Redo Stack**: Tabufinery records up to **25 sequential operations** in your session. You can click **Undo** to step backward or **Redo** to re-apply any action at any time from the toolbar.";
        }

        if (q.contains("clean") || q.contains("missing") || q.contains("duplicate") || q.contains("trim") || q.contains("null") || q.contains("transform")) {
            return "### Supported Data Cleaning Operations\n\n" +
                    "In the **Data Workspace**, open the **Clean & Transform** panel to use:\n\n" +
                    "- **Missing Value Imputation**: Fill missing or empty cells using **Mean**, **Median**, **Mode**, **Zero (0)**, or a **Custom Value**.\n" +
                    "- **Drop Empty Cells**: Safely remove entire rows or columns that contain null values.\n" +
                    "- **Remove Duplicates**: Eliminate exact duplicate rows across all columns or selected key columns.\n" +
                    "- **Text Casing & Trimming**: Trim accidental whitespace, convert to UPPERCASE, lowercase, or Title Case.\n" +
                    "- **Column Renaming & Type Cast**: Rename headers and convert columns between Text, Number, Date, and Boolean.\n\n" +
                    "All operations show a live preview before applying!";
        }

        if (q.contains("export") || q.contains("download") || q.contains("pdf") || q.contains("csv") || q.contains("report")) {
            return "### Exporting Data & Reports\n\n" +
                    "Head to the **Export Hub** page to download your refined data in multiple formats:\n\n" +
                    "1. **Excel Workbook (.xlsx)**: Downloads your complete workbook preserving all applied cleaning and modifications.\n" +
                    "2. **CSV File (.csv)**: Generates a standard comma-separated text file of the active sheet for data pipelines and database imports.\n" +
                    "3. **Executive PDF Report**: Creates a styled report summarizing dataset health, column statistics, row counts, and data quality scores.";
        }

        if (q.contains("project") || q.contains("save") || q.contains("saved") || q.contains("resume") || q.contains("load")) {
            return "### Saved Projects & Database Persistence\n\n" +
                    "- **Save Your Work**: Click the **Projects** button in the top navigation bar or Data Workspace, then select **Save Current Project**. Give it a descriptive name.\n" +
                    "- **Persistence**: Your workbook, active sheet, and transformation state are stored in the secure persistent database.\n" +
                    "- **Resume Later**: Reopen saved projects at any time from the **Saved Projects** modal, rename them, or delete old ones.\n" +
                    "- **Multi-tenant Privacy**: Each user's saved projects are strictly private and isolated to their account.";
        }

        if (q.contains("analyst") || q.contains("insights") || q.contains("gemini") || q.contains("quality score") || q.contains("ai assistant")) {
            return "### AI Analyst & Insights vs. Support Chatbot\n\n" +
                    "- **AI Data Analyst**: Located on the **AI Analyst & Insights** page. It profiles your active spreadsheet data (calculating data quality scores, missing cell percentages, and distributions) and lets you ask questions like *'What are the highest revenue items?'* or *'Summarize outliers'*. It uses verified statistics from your active workbook.\n" +
                    "- **AI Support Chatbot** *(This Widget)*: Helps you navigate Tabufinery SaaS, troubleshoot technical issues, understand feature limits, and submit support tickets to engineering.";
        }

        if (q.contains("error") || q.contains("fail") || q.contains("bug") || q.contains("not working") || q.contains("issue") || q.contains("problem") || q.contains("troubleshoot")) {
            return "### Troubleshooting Common Issues\n\n" +
                    "- **File rejected upon upload**: Ensure the file is a standard `.xlsx` (not legacy `.xls` or password-protected) and under 10MB.\n" +
                    "- **Session expired / 401 Unauthorized**: If your token expired, simply sign in again to restore access.\n" +
                    "- **Nothing happens when clicking Clean**: Check if you clicked **Apply** on the preview card at the top of the grid.\n" +
                    "- **Still facing an issue?** You can submit a support ticket right here by clicking **Submit Ticket** below, or email us at **support0912@gmail.com**.";
        }

        if (q.contains("password") || q.contains("account") || q.contains("login") || q.contains("admin") || q.contains("reset")) {
            return "### Account & Security Management\n\n" +
                    "- **Forgot Password**: Click the **Forgot Password?** link on the Sign In screen to receive a secure password reset token.\n" +
                    "- **Multi-Tenancy**: All uploaded data and sessions are strictly isolated per user account.\n" +
                    "- **Admin Console**: Administrators (`ROLE_ADMIN`) can manage accounts and review system metrics via the **Admin Console** tab in the sidebar.";
        }

        return "I am the Tabufinery Support Assistant. I can assist you with:\n\n" +
                "1. **Uploading & Formatting**: File limits (.xlsx up to 10MB) and multi-sheet navigation.\n" +
                "2. **Data Cleaning**: Imputing missing values, removing duplicates, and casing.\n" +
                "3. **Change Preview & Undo/Redo**: Before/after inspection and history controls.\n" +
                "4. **Export Hub**: Cleaned XLSX, CSV, and Executive PDF reports.\n" +
                "5. **Saved Projects**: Storing and resuming datasets from the database.\n\n" +
                "If you need engineering assistance, feel free to use the **Submit Ticket** button below or contact `support0912@gmail.com`.";
    }
}
