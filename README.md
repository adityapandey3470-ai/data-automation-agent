# Data Automation Agent

> AI Business Automation Platform — Backend Foundation + Excel Ingestion + Data Analysis + Data Cleaning

## Project Overview

A production-oriented backend designed to power an AI-driven business automation platform. The future system will allow users to upload Excel/CSV files and issue natural-language commands like:

- "Analyze this Excel file"
- "Remove duplicate rows"
- "Create a sales dashboard"

This repository contains the **backend foundation**, **Excel file ingestion**, **deterministic data analysis engine**, and **data cleaning/transformation engine**. LLM integration, dashboard generation, and advanced processing will be added in subsequent phases.

---

## Technology Stack

| Technology            | Version / Details              |
|-----------------------|-------------------------------|
| Java                  | 21                            |
| Spring Boot           | 3.5.x                        |
| Maven                 | 3.9+                          |
| Spring Web            | REST API                      |
| Spring Data JPA       | ORM / Repository layer        |
| PostgreSQL            | Primary database              |
| Flyway                | Database migrations           |
| Lombok                | Boilerplate reduction         |
| Bean Validation       | Input validation              |
| Spring Boot Actuator  | Health & info endpoints       |
| SpringDoc OpenAPI     | Swagger UI & API docs         |
| Apache POI            | 5.3.0 — .xlsx file processing |

---

## Architecture

```
Excel File (.xlsx)
       │
       ▼
  ExcelFileService ──→ ExcelFileResponse (preview DTO)
       │
       ▼
  ExcelWorkbookData (POI-independent internal model)
       │
       ├──→ DataAnalysisService ──→ AnalysisResult / ColumnStatistics / GroupResult
       │
       └──→ DataCleaningService ──→ CleaningResult (new cleaned workbook)
                    │
                    ▼
              Cleaned ExcelWorkbookData (original unchanged)
```

Both engines are completely decoupled from Apache POI and operate on the generic `ExcelWorkbookData` model. Cleaning operations are immutable — they always return a new workbook, leaving the original unchanged.

### Package Structure

```
com.aditya.dataautomation
│
├── DataAutomationApplication.java
│
├── model/excel/                              # POI-independent data model
│   ├── ExcelWorkbookData.java
│   └── ExcelSheetData.java
│
├── analysis/                                 # Data analysis engine
│   ├── model/
│   ├── service/
│   │   ├── DataAnalysisService.java
│   │   └── impl/DataAnalysisServiceImpl.java
│   └── exception/AnalysisException.java
│
├── cleaning/                                 # Data cleaning engine
│   ├── model/
│   │   ├── CleaningOperation.java            # Enum
│   │   ├── CleaningResult.java
│   │   ├── DuplicateStrategy.java            # Enum
│   │   ├── MissingValueStrategy.java         # Enum
│   │   ├── TargetDataType.java               # Enum
│   │   └── TransformationRequest.java
│   ├── service/
│   │   ├── DataCleaningService.java
│   │   └── impl/DataCleaningServiceImpl.java
│   ├── controller/DataCleaningController.java
│   └── exception/CleaningException.java
│
├── controller/
│   ├── HealthController.java
│   ├── ExcelFileController.java
│   └── DataAnalysisController.java
│
├── service/
│   ├── ExcelFileService.java
│   ├── WorkbookSessionHolder.java
│   └── impl/ExcelFileServiceImpl.java
│
├── dto/
│   ├── analysis/
│   └── excel/
│
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── InvalidExcelFileException.java
│
└── common/AppConstants.java
```

---

## Data Analysis Engine

### Supported Operations

| Operation | Description |
|-----------|-------------|
| COUNT | Count total rows |
| COUNT_NON_EMPTY | Count non-null values in a column |
| COUNT_NULLS | Count null/missing values |
| DISTINCT | Unique non-null values |
| SUM | Sum of numeric column (BigDecimal) |
| AVERAGE | Average of numeric column |
| MIN / MAX | Min/max value |
| FILTER | Filter rows by multiple conditions (AND) |
| SORT | Sort by one or multiple columns |
| GROUP BY | Group + aggregate (COUNT/SUM/AVG/MIN/MAX) |
| DUPLICATES | Detect duplicate rows |
| STATISTICS | Comprehensive column statistics |

### Filter Operators

`EQUALS`, `NOT_EQUALS`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `IS_NULL`, `IS_NOT_NULL`

---

## Data Cleaning Engine

### Supported Operations

| Operation | Description |
|-----------|-------------|
| **REMOVE_DUPLICATES** | Remove duplicate rows by all or selected columns |
| **HANDLE_MISSING_VALUES** | Drop rows or fill missing values |
| **TRIM** | Remove leading/trailing whitespace |
| **NORMALIZE_WHITESPACE** | Collapse internal whitespace to single space |
| **LOWERCASE** | Convert string column to lowercase |
| **UPPERCASE** | Convert string column to uppercase |
| **REPLACE_VALUE** | Replace specific values in a column |
| **RENAME_COLUMN** | Rename a column |
| **REMOVE_COLUMN** | Remove a column from the sheet |
| **REORDER_COLUMNS** | Change column order |
| **CONVERT_TYPE** | Convert values to STRING/INTEGER/DECIMAL/BOOLEAN/DATE/DATETIME |

### Duplicate Strategies

| Strategy | Description |
|----------|-------------|
| `KEEP_FIRST` | Keep the first occurrence (default) |
| `KEEP_LAST` | Keep the last occurrence |

### Missing Value Strategies

| Strategy | Description |
|----------|-------------|
| `DROP_ROW` | Remove rows where column is null/empty |
| `FILL_CONSTANT` | Replace with a supplied constant |
| `FILL_NUMERIC_ZERO` | Replace with 0 |
| `FORWARD_FILL` | Use previous non-empty value |
| `BACKWARD_FILL` | Use next non-empty value |

### Example: Before & After

**Before (raw upload):**

| Customer | Email | City | Revenue |
|----------|-------|------|---------|
| `"  Rahul  "` | rahul@gmail.com | `" Delhi "` | "12000" |
| Rahul | rahul@gmail.com | Delhi | "12000" |
| `" Priya "` | priya@gmail.com | *null* | "15000" |
| `" Amit "` | *null* | Noida | "8000" |

**After pipeline: TRIM → REMOVE_DUPLICATES(Email) → FILL_CONSTANT(City, "Unknown"):**

| Customer | Email | City | Revenue |
|----------|-------|------|---------|
| Rahul | rahul@gmail.com | Delhi | "12000" |
| Priya | priya@gmail.com | Unknown | "15000" |
| Amit | *null* | Noida | "8000" |

### Key Design Rules

1. **Immutable transformations** — original workbook is NEVER mutated
2. **No POI dependency** — cleaning engine operates on `ExcelWorkbookData`
3. **Structured inputs** — designed for future AI tool calling
4. **Deterministic** — same input always produces the same output
5. **Type-safe conversions** — invalid conversions fail clearly, never corrupt data

---

## API Endpoints

### Upload Excel

```
POST /api/v1/files/excel
Content-Type: multipart/form-data
```

### Analysis Endpoints

```
POST /api/v1/analysis/statistics
POST /api/v1/analysis/filter
POST /api/v1/analysis/sort
POST /api/v1/analysis/group
POST /api/v1/analysis/duplicates
```

### Cleaning Endpoints

```
POST /api/v1/cleaning/duplicates       — Remove duplicate rows
POST /api/v1/cleaning/missing-values   — Handle missing values
POST /api/v1/cleaning/strings          — String cleaning (TRIM/NORMALIZE/LOWER/UPPER)
POST /api/v1/cleaning/replace          — Replace values
POST /api/v1/cleaning/rename-column    — Rename a column
POST /api/v1/cleaning/remove-column    — Remove a column
POST /api/v1/cleaning/convert-type     — Convert column data type
POST /api/v1/cleaning/transform        — Execute multiple operations (pipeline)
```

### Cleaning API Examples

**Remove duplicates by email:**

```json
POST /api/v1/cleaning/duplicates
{
  "sheetName": "Customers",
  "columns": ["Email"],
  "duplicateStrategy": "KEEP_FIRST"
}
```

**Fill missing values:**

```json
POST /api/v1/cleaning/missing-values
{
  "sheetName": "Customers",
  "columnName": "City",
  "missingValueStrategy": "FILL_CONSTANT",
  "value": "Unknown"
}
```

**Trim whitespace:**

```json
POST /api/v1/cleaning/strings
{
  "sheetName": "Customers",
  "operation": "TRIM",
  "columnName": "Customer"
}
```

**Replace value:**

```json
POST /api/v1/cleaning/replace
{
  "sheetName": "Customers",
  "columnName": "City",
  "oldValue": "Delhi",
  "newValue": "New Delhi"
}
```

**Convert type:**

```json
POST /api/v1/cleaning/convert-type
{
  "sheetName": "Customers",
  "columnName": "Revenue",
  "targetType": "INTEGER"
}
```

**Pipeline (multiple operations):**

```json
POST /api/v1/cleaning/transform
[
  {
    "sheetName": "Customers",
    "operation": "TRIM",
    "columnName": "Customer"
  },
  {
    "sheetName": "Customers",
    "operation": "REMOVE_DUPLICATES",
    "columns": ["Email"]
  },
  {
    "sheetName": "Customers",
    "operation": "HANDLE_MISSING_VALUES",
    "columnName": "City",
    "missingValueStrategy": "FILL_CONSTANT",
    "value": "Unknown"
  }
]
```

**CleaningResult response example:**

```json
{
  "operation": "REMOVE_DUPLICATES",
  "affectedSheet": "Customers",
  "rowsBefore": 4,
  "rowsAfter": 3,
  "rowsRemoved": 1,
  "message": "Removed 1 duplicate row(s) using strategy KEEP_FIRST"
}
```

### Other Endpoints

```
GET  /api/v1/health                   — Health check
GET  /actuator/health                 — Actuator health
     http://localhost:8080/swagger-ui.html  — Swagger UI
```

---

## How to Run

```bash
mvn clean compile        # Compile
mvn test                 # Run tests (no PostgreSQL needed)
mvn spring-boot:run      # Start app (needs PostgreSQL + env vars)
mvn clean package        # Package JAR
```

### Required Environment Variables

| Variable | Example |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/data_automation` |
| `DB_USERNAME` | `your_user` |
| `DB_PASSWORD` | `your_password` |

---

## Current Assumptions

1. **First non-empty row is the header row.**
2. **Blank headers** are auto-named `Column_1`, `Column_2`, etc.
3. **Completely blank rows** are skipped.
4. **Formulas are not evaluated.** Only cached results are read.
5. **Macros are never executed.**
6. **Files are processed in memory.** No file is saved to disk or database.
7. **Only `.xlsx` format** is supported.
8. **Numeric operations use BigDecimal** for financial accuracy.
9. **Null values sort last** in ascending order.
10. **Column names are case-insensitive** in analysis/cleaning queries.
11. **The last uploaded workbook** is stored in memory for analysis/cleaning.
12. **Cleaning operations are immutable** — original data is never modified.
13. **Pipeline operations are sequential** — each operation's output feeds the next.
14. **Invalid type conversions** throw clear errors (never silently corrupt data).

---

## Limitations

- No file persistence — files exist only in memory until replaced or server restart
- Single-user session — only one workbook in memory at a time
- No CSV or .xls support
- No formula evaluation (cached values only)
- No multi-user concurrency guarantees yet
- GROUP BY supports one aggregation column at a time
- Cleaning does not persist to disk — cleaned data is in memory only

---

## License

Private — not open source.
