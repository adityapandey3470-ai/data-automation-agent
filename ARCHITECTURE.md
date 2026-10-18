# Data Automation Agent — Architecture & Conventions

## Package Structure Convention

Ye project **STRICTLY feature-based package structure** follow karta hai. Har feature/domain ka apna dedicated package hai jisme uske required internal sub-packages hote hain:

```text
com.aditya.dataautomation.<feature>/
├── controller/         # REST Controllers handling HTTP requests for the feature
├── service/            # Feature service interfaces
│   └── impl/           # Business logic service implementations
├── dto/                # Request & Response Data Transfer Objects
├── entity/             # JPA entity mappings specific to the feature
├── repository/         # Spring Data JPA repositories specific to the feature
├── model/              # Domain/in-memory models & aggregates
└── exception/          # Custom exceptions specific to the feature
```

### Active Feature Packages:
- **`auth/`**: User registration, authentication, JWT token lifecycle, and role security.
- **`project/`**: Workspace project metadata, saves, and persistent workbook states.
- **`dashboard/`**: Dynamic dashboard analytics, KPI metrics, chart generations, and cross-filters.
- **`cleaning/`**: Data cleaning rules, trim/casing/dedup operations, transformations, and undo/redo states.
- **`analysis/`**: Grouping, statistics, filtering, sorting, duplicate scans, and summaries.
- **`admin/`**: User management, system-wide metrics, and administrative role assignments.
- **`support/`**: AI support assistant chat and support ticket workflows.
- **`ai/`**: AI Analyst intelligence endpoints, automatic formula generator, and data insights.
- **`excel/`**: Excel (.xlsx) file parsing, workbook inspection, sheet profiling, and POI processing.
- **`workbook/`**: Active session holding, multi-file working sessions, undo/redo stack persistence.
- **`export/`**: Excel (.xlsx), CSV, and PDF report generation.
- **`health/`**: Application health check endpoints and uptime metrics.
- **`system/`**: System metadata and global key-value runtime configuration.

---

### Core Architectural Rules:

1. **RULE**: Koi bhi NAYI class kabhi bhi top-level generic package (jaise bina-feature-ke `controller/`, `dto/`, `model/`, `service/`, `entity/`, `repository/`) mein **NAHI** banegi — hamesha uske respective feature-package ke andar banegi.
2. **RULE**: Agar koi naya feature introduce hota hai jiska existing domain nahi hai, to pehle naya feature-package banao (jaise `export/` ya `workbook/` ki tarah).
3. **Cross-Cutting Shared Components**: Sirf genuinely cross-cutting aur platform-wide shared cheezein top-level par rahengi:
   - `common/`: Global constants (`AppConstants.java`), universal helpers.
   - `config/`: Spring security, CORS, OpenAPI, and typed properties (`config/properties/**`).
   - `dto/ErrorResponse.java`: Unified error response envelope shared across all REST controllers.
   - `exception/GlobalExceptionHandler.java`: Centralized controller advice for exception-to-HTTP mapping.
   - `exception/InvalidRequestException.java`: Base shared validation runtime exception.
   - `DataAutomationApplication.java`: Spring Boot bootstrap main class.
