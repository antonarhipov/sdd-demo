# Requirements Analysis

**Pipeline position:** proposal → *requirements* → acceptance criteria → technical constraints → spec review → tasks

**Source:** `spec/proposal.md`

---

## 1. AMBIGUITIES (Resolved)

| # | Ambiguity | Resolution |
|---|-----------|------------|
| A1 | Format of the `datetime` column in CSV | **ISO 8601** (e.g., `2024-01-15T14:30:00`) |
| A2 | Data type and precision of `temp` column | **Decimal with 1 decimal place** — `DECIMAL(5,1)` in MySQL, `BigDecimal` in Java |
| A3 | What "reported" means for duplicates | Duplicates are **logged** and included in the final summary count |
| A4 | "Print the summary" — where? | Console output (standard Spring Batch job completion logging) |

---

## 2. MISSING INFORMATION (Resolved)

| # | Gap | Resolution |
|---|-----|------------|
| M1 | How the application discovers CSV files | A **directory path** configured via `application.properties` (e.g., `batch.input.dir=data/input`) |
| M2 | Single file vs. multiple files | Process **all `.csv` files** found in the configured directory |
| M3 | Duplicate check scope | **DB-only** — check each `(name, datetime)` pair against existing database records before inserting |
| M4 | Behavior on malformed rows | **Skip and log** — log each malformed row (missing/unparseable columns) to console, continue processing |

---

## 3. IMPLICIT ASSUMPTIONS (Made Explicit)

| # | Assumption |
|---|------------|
| AS1 | The application is a **Spring Boot** application with Spring Batch auto-configuration |
| AS2 | MySQL is available and connection details are provided via `application.properties` |
| AS3 | The database table must be **created by the application** (schema auto-creation or Liquibase/Flyway migration) |
| AS4 | CSV files have a **header row** with column names (including at minimum `name`, `datetime`, `temp`; extra columns are ignored) |
| AS5 | The job is triggered **on application startup** (not via REST endpoint or scheduler) |
| AS6 | Java **records** are used as the data model (as specified), not traditional POJOs |
| AS7 | `name` and `datetime` together form the **unique key** — neither alone is unique |

---

## 4. EDGE CASES

| # | Scenario | Expected Behavior |
|---|----------|-------------------|
| E1 | Empty CSV file (no rows, only header) | Job completes with 0 inserted, 0 duplicates |
| E2 | CSV with no header row | Treated as malformed; all rows skipped and logged |
| E3 | Row missing `name`, `datetime`, or `temp` | Skipped and logged (per M4 resolution) |
| E4 | `temp` value is non-numeric (e.g., `"N/A"`) | Skipped and logged |
| E5 | `datetime` value does not conform to ISO 8601 | Skipped and logged |
| E6 | Two rows in the same CSV share the same `(name, datetime)` pair | The second occurrence is treated as a duplicate (DB check will catch it after the first is inserted, or via in-flight dedup) |
| E7 | Configured input directory does not exist | Job fails with a clear error message at startup |
| E8 | Configured input directory contains no `.csv` files | Job completes with 0 inserted, 0 duplicates, logs a warning |
| E9 | Multiple CSV files in the directory | All files are processed; summary covers all files combined |
| E10 | DB insert fails for reasons other than duplicate (e.g., connection lost) | Job fails; Spring Batch records failure in its metadata tables |

---

## 5. FUNCTIONAL REQUIREMENTS

### FR1 — CSV Reading
- Read all `.csv` files from the directory specified by `batch.input.dir` in `application.properties`
- Parse columns: `name` (String), `datetime` (ISO 8601 → `LocalDateTime`), `temp` (1 decimal place → `BigDecimal`)
- Ignore all columns other than `name`, `datetime`, and `temp`
- Use a Java **record** as the data model for a parsed CSV row

### FR2 — Duplicate Detection
- Before inserting each record, check the database for an existing row with the same `(name, datetime)` pair
- If a duplicate is found: skip the insert, log the duplicate, increment the duplicate counter

### FR3 — Data Insertion
- Insert non-duplicate records into the MySQL database via JDBC
- The target table must store: `name` (VARCHAR(255)), `datetime` (DATETIME), `temp` (DECIMAL(5,1))

### FR4 — Error Handling
- Malformed rows (unparseable `datetime`, non-numeric `temp`, missing required fields) are skipped
- Each skipped row is logged with enough detail to identify the source row

### FR5 — Summary Report
- On job completion (success or failure), print to console using the format:
  `Batch complete — status: <STATUS>, inserted: <N>, duplicates: <N>, malformed: <N>`
  where `<STATUS>` is `COMPLETED` or `FAILED`
- If the job failed, partial counts accumulated up to the point of failure are reported
- Output is produced by a `JobExecutionListener` writing via SLF4J at `INFO` level

### FR6 — Testing
- Integration tests use **Testcontainers** with a real MySQL container
- H2 in-memory database must **not** be used in any test

### FR7 — Language & Framework
- Java 21
- Spring Boot + Spring Batch
- JDBC (not JPA/Hibernate)
- Java records for data transfer objects
