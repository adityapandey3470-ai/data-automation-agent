# Data Automation Agent

> AI Business Automation Platform — Backend Foundation + Excel Ingestion + Data Analysis Engine

## Project Overview

A production-oriented backend designed to power an AI-driven business automation platform. The future system will allow users to upload Excel/CSV files and issue natural-language commands like:

- "Analyze this Excel file"
- "Remove duplicate rows"
- "Create a sales dashboard"

This repository contains the **backend foundation**, the **Excel file ingestion layer**, and the **deterministic data analysis engine**. LLM integration, dashboard generation, and advanced processing will be added in subsequent phases.

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
  ExcelFileService  ──→  ExcelFileResponse (preview DTO)
       │
       ▼
  ExcelWorkbookData  (POI-independent internal model)
       │
       ▼
  DataAnalysisService  ──→  AnalysisResult / ColumnStatistics / GroupResult
       │
       ▼
  DataAnalysisController  ──→  JSON API responses
```

The analysis engine is completely decoupled from Apache POI and operates on the generic `ExcelWorkbookData` model. This design enables future AI agents to call analysis methods directly.

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
│   │   ├── AnalysisResult.java
│   │   ├── ColumnStatistics.java
│   │   ├── FilterCondition.java
│   │   ├── FilterOperator.java               # Enum
│   │   ├── SortCondition.java
│   │   ├── SortDirection.java                # Enum
│   │   ├── AggregationFunction.java          # Enum
│   │   └── GroupResult.java
│   ├── service/
│   │   ├── DataAnalysisService.java
│   │   └── impl/DataAnalysisServiceImpl.java
│   └── exception/
│       └── AnalysisException.java
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
│   │   ├── StatisticsRequest.java
│   │   ├── FilterRequest.java
│   │   ├── SortRequest.java
│   │   ├── GroupRequest.java
│   │   └── DuplicateRequest.java
│   └── excel/
│       ├── ExcelFileResponse.java
│       └── ExcelSheetResponse.java
│
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── InvalidExcelFileException.java
│
└── common/
    └── AppConstants.java
```

---

## Prerequisites

- **Java 21** (or higher)
- **Maven 3.9+**
- **PostgreSQL 14+** running locally or remotely

---

## Environment Variables

| Variable       | Description                              | Example                                          |
|----------------|------------------------------------------|--------------------------------------------------|
| `DB_URL`       | JDBC URL for PostgreSQL                  | `jdbc:postgresql://localhost:5432/data_automation`|
| `DB_USERNAME`  | Database username                        | `your_user`                                      |
| `DB_PASSWORD`  | Database password                        | `your_password`                                  |
| `SERVER_PORT`  | Application port (optional, default 8080)| `8080`                                           |

---

## How to Run

```bash
mvn clean compile        # Compile
mvn test                 # Run tests (no PostgreSQL needed)
mvn spring-boot:run      # Start app (needs PostgreSQL + env vars)
mvn clean package        # Package JAR
```

---

## Data Analysis Engine

### Supported Operations

| Operation | Description |
|-----------|-------------|
| COUNT | Count total rows |
| COUNT_NON_EMPTY | Count non-null values in a column |
| COUNT_NULLS | Count null/missing values in a column |
| DISTINCT | Unique non-null values in a column |
| SUM | Sum of numeric column (BigDecimal) |
| AVERAGE | Average of numeric column (BigDecimal) |
| MIN | Minimum value |
| MAX | Maximum value |
| FILTER | Filter rows by multiple conditions (AND) |
| SORT | Sort by one or multiple columns |
| GROUP BY | Group + aggregate (COUNT/SUM/AVG/MIN/MAX) |
| DUPLICATES | Detect duplicate rows by all or selected columns |
| STATISTICS | Comprehensive column statistics |

### Filter Operators

| Operator | Description |
|----------|-------------|
| `EQUALS` | Exact match (case-insensitive for strings) |
| `NOT_EQUALS` | Not equal |
| `GREATER_THAN` | Numeric/date/string comparison |
| `GREATER_THAN_OR_EQUAL` | >= comparison |
| `LESS_THAN` | < comparison |
| `LESS_THAN_OR_EQUAL` | <= comparison |
| `CONTAINS` | String contains (case-insensitive) |
| `STARTS_WITH` | String starts with |
| `ENDS_WITH` | String ends with |
| `IS_NULL` | Value is null |
| `IS_NOT_NULL` | Value is not null |

### Aggregation Functions

`COUNT`, `SUM`, `AVERAGE`, `MIN`, `MAX`

---

## API Endpoints

### 1. Upload Excel File

```
POST /api/v1/files/excel
Content-Type: multipart/form-data
```

```bash
curl -X POST http://localhost:8080/api/v1/files/excel -F "file=@sales.xlsx"
```

### 2. Column Statistics

```
POST /api/v1/analysis/statistics
```

```json
{
  "sheetName": "Sales",
  "column": "Revenue"
}
```

Response:

```json
{
  "operation": "STATISTICS",
  "sheetName": "Sales",
  "totalRows": 5,
  "statistics": {
    "column": "Revenue",
    "dataType": "NUMERIC",
    "count": 5,
    "nullCount": 0,
    "distinctCount": 5,
    "min": 2000,
    "max": 120000,
    "sum": 196000,
    "average": 39200.0000
  }
}
```

### 3. Filter Rows

```
POST /api/v1/analysis/filter
```

```json
{
  "sheetName": "Sales",
  "conditions": [
    {"column": "Region", "operator": "EQUALS", "value": "Delhi"},
    {"column": "Revenue", "operator": "GREATER_THAN", "value": 10000}
  ]
}
```

### 4. Sort Rows

```
POST /api/v1/analysis/sort
```

```json
{
  "sheetName": "Sales",
  "sortBy": [
    {"column": "Region", "direction": "ASC"},
    {"column": "Revenue", "direction": "DESC"}
  ]
}
```

### 5. Group By

```
POST /api/v1/analysis/group
```

```json
{
  "sheetName": "Sales",
  "groupByColumn": "Region",
  "aggregationColumn": "Revenue",
  "aggregation": "SUM"
}
```

Response:

```json
{
  "operation": "GROUP_BY",
  "sheetName": "Sales",
  "totalRows": 5,
  "groupResult": {
    "groupByColumn": "Region",
    "aggregationColumn": "Revenue",
    "aggregation": "SUM",
    "groups": [
      {"key": "Delhi", "count": 3, "aggregationValue": 189000},
      {"key": "Noida", "count": 2, "aggregationValue": 7000}
    ]
  }
}
```

### 6. Detect Duplicates

```
POST /api/v1/analysis/duplicates
```

```json
{
  "sheetName": "Sales",
  "columns": ["Product"]
}
```

### 7. Health Check

```
GET /api/v1/health
```

### 8. Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## Current Assumptions

1. **First non-empty row is the header row.**
2. **Blank headers** are auto-named `Column_1`, `Column_2`, etc.
3. **Completely blank rows** are skipped.
4. **Formulas are not evaluated.** Only the cached result is read.
5. **Macros are never executed.**
6. **Files are processed in memory.** No file is saved to disk or database.
7. **Only `.xlsx` format** is supported.
8. **Numeric operations use BigDecimal** for financial accuracy.
9. **Null values sort last** in ascending order.
10. **Column names are case-insensitive** in analysis queries.
11. **The last uploaded workbook** is stored in memory for analysis.
12. **Multiple filter conditions** are combined with AND logic.
13. **Numeric operations on non-numeric columns** return meaningful errors (not silent zeros).

---

## Limitations

- No file persistence — files exist only in memory until replaced or server restarts
- Single-user session — only one workbook in memory at a time
- No CSV or .xls support
- No formula evaluation (cached values only)
- No multi-user concurrency guarantees yet
- GROUP BY supports one aggregation column at a time

---

## License

Private — not open source.
