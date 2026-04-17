# Technical Constraints

## Overview

Technical constraints for the **CSV Temperature Data Import** batch job.

- Requirements: [spec/requirements.md](requirements.md)
- Acceptance Criteria: [spec/acceptance_criteria.md](acceptance_criteria.md)
- Proposal: [spec/proposal.md](proposal.md)

**Tech Stack:** Java 21, Spring Boot, Spring Batch, JDBC, MySQL, Testcontainers

---

## Project-Level Constraints

These are project-wide rules that apply to all code in this repository.

- MUST target Java 21 (source and target compatibility set to `21` in the build tool)
- MUST use Spring Boot with Spring Batch auto-configuration
- MUST NOT introduce JPA, Hibernate, or any ORM framework
- MUST NOT use H2 or any in-memory database substitute for MySQL in any context (production or test)

---

## Feature-Level Constraints

### Project Structure

- MUST follow standard Maven/Gradle project layout: `src/main/java`, `src/main/resources`, `src/test/java`
- MUST place all application source under a single root package (e.g., `com.example.batch` or equivalent)
- MUST place batch job configuration in a dedicated sub-package (e.g., `...batch` or `...config`)
- MUST place the data model record(s) in a dedicated sub-package (e.g., `...model` or `...domain`)
- MUST place database schema migration scripts under `src/main/resources` (Flyway: `db/migration/`, or equivalent)
- SHOULD separate reader, processor, and writer components into distinct classes rather than inline lambdas

### Component Design

- MUST represent a parsed CSV row as a Java **record** (not a class with getters/setters) — per FR7, AC-7.1
- MUST define a Spring Batch `Job` bean and at least one `Step` bean in a `@Configuration` class
- MUST implement CSV reading using Spring Batch's `FlatFileItemReader` or a subclass; MUST NOT read files with manual `BufferedReader` loops outside the Batch framework
- MUST implement a custom `ItemProcessor` (or equivalent) responsible for:
  - Parsing `datetime` (String → `LocalDateTime`, ISO 8601)
  - Parsing `temp` (String → `BigDecimal`, 1 decimal place)
  - Detecting and handling malformed rows (returning `null` to skip, and logging)
- MUST implement duplicate detection inside the `ItemWriter` using a JDBC query against the database before each insert; items MUST be processed sequentially within the writer (check-then-insert one item at a time) so that intra-chunk duplicates are detected correctly — do NOT collect all non-duplicates first and then batch-insert, as both rows would pass the pre-insert check before either is committed
- MUST implement a `JobExecutionListener` (or `StepExecutionListener`) to print the final summary to console on job completion — per FR5, AC-6.1
- MUST NOT implement duplicate detection using a `UNIQUE` constraint violation as the primary detection mechanism (i.e., must check before insert, not rely on catching `DuplicateKeyException` as the normal flow)
- SHOULD use constructor injection (not field injection) for all Spring-managed beans

### Technology Decisions

- MUST use **Spring Batch** for all batch orchestration (reader → processor → writer pipeline) — reason: specified in proposal and FR7
- MUST use **JDBC** (`JdbcTemplate` or `NamedParameterJdbcTemplate`) for all database operations — reason: FR7 prohibits JPA/Hibernate
- MUST use **Flyway** or **Spring Boot schema initialization** (`schema.sql`) to create the database table on startup — reason: AS3, AC-4.2
- MUST configure `spring.batch.initialize-schema=always` (or equivalent) to ensure Spring Batch metadata tables are created on startup — required for AC-4.3 failure recording
- MUST configure the input directory via `batch.input.dir` property in `application.properties` — reason: M1
- MUST use `MultiResourceItemReader` (or equivalent) to process all `.csv` files in the configured directory — reason: M2, AC-1.2
- MUST use **Testcontainers** (`mysql` module) for integration tests — reason: FR6, AC-8.1
- SHOULD use `spring-boot-starter-batch` and let Spring Boot manage `BatchAutoConfiguration`
- SHOULD externalize all configurable values (input dir, DB URL, etc.) via `application.properties` / `application.yml`
- MUST NOT hardcode file paths, database credentials, or table names in Java source code

### Code Style

- MUST use `LocalDateTime` for the `datetime` field in the data record — per FR1
- MUST use `BigDecimal` for the `temp` field in the data record — per FR1 (A2)
- MUST use `DECIMAL(5,1)` column type in the database schema — per FR3
- MUST use `VARCHAR(255)` for the `name` column and `DATETIME` for the `datetime` column — per FR3
- MUST log malformed row details at `WARN` level, including enough context to identify the source row (file name, row content or line number, reason) — per FR4, AC-5.1–AC-5.3
- MUST log duplicate records at `INFO` or `WARN` level — per FR2
- MUST NOT use `System.out.println` for logging; use SLF4J (`LoggerFactory`) for all logging including the final job summary (use `INFO` level — per OD3)
- SHOULD name the database table `temperature_records` or similarly descriptive name
- SHOULD use `COMPOSITE_UNIQUE` or a named unique index on `(name, datetime)` as a database-level integrity guard (secondary to the application-level check)

### Testing Strategy

- MUST write integration tests that spin up a real MySQL container via Testcontainers — per FR6, AC-8.1
- MUST include a test verifying duplicate detection: pre-seed the database, run the job, assert duplicate counter is correct and no duplicate rows are inserted — per AC-8.3
- MUST include a test verifying malformed row handling: supply a CSV with malformed rows, assert malformed counter is correct and job does not abort — per AC-8.4
- MUST include a test verifying DB connection failure behavior: simulate a connection failure (e.g., configure an invalid datasource or stop the container mid-job), assert the job status is `FAILED`, and assert Spring Batch records the failure in its metadata tables — per AC-4.3, E10
- MUST NOT use `@SpringBootTest` with an H2 datasource or any `test` profile that substitutes MySQL with H2 — per AC-8.2
- SHOULD include tests for each edge case category: empty CSV (E1), no CSV files (E8), multiple files (E9), missing directory (E7)
- SHOULD assert the console/log output of the summary report in at least one integration test; expected format: `Batch complete — status: COMPLETED, inserted: <N>, duplicates: <N>, malformed: <N>`
- SHOULD include a test verifying that rows with the same `name` but different `datetime` values are both inserted — per AC-3.4
- SHOULD use `@SpringBatchTest` or `JobLauncherTestUtils` to launch and assert Spring Batch jobs in tests
- MUST NOT mock the database in integration tests — tests must exercise real SQL against the Testcontainers MySQL instance

### Error Handling

- MUST validate that `batch.input.dir` exists in a `JobExecutionListener.beforeJob()` or `JobParametersValidator` so the job never enters `STARTED` state; if the directory does not exist, fail with a descriptive error before any processing — per E7, AC-1.4
- MUST configure Spring Batch to skip malformed items (via `faultTolerant().skip(...).skipLimit(...)` or equivalent) rather than failing the step — per FR4, M4
- MUST track skip counts: malformed rows via the skip listener or step execution context, duplicates via explicit counter — per FR5
- MUST NOT silently discard data on DB connection failure; the job must fail and Spring Batch must record the failure in its metadata tables — per E10, AC-4.3
- SHOULD log a `WARN` when the input directory contains no `.csv` files and complete normally — per E8, AC-1.3

---

## Constraint-Criteria Cross-Reference

| Constraint Area | Acceptance Criteria Covered |
|---|---|
| Java record for data model | AC-7.1 |
| `batch.input.dir` property | AC-1.1, AC-1.4 |
| `MultiResourceItemReader` for all `.csv` files | AC-1.2, AC-1.3, AC-6.2 |
| `FlatFileItemReader` for CSV parsing | AC-2.1, AC-2.2, AC-2.3, AC-2.4 |
| Application-level duplicate check before insert | AC-3.1, AC-3.2, AC-3.3, AC-3.4 |
| Flyway/schema.sql table creation | AC-4.2 |
| JDBC for inserts | AC-4.1 |
| DB failure propagated (no silent discard) | AC-4.3 |
| Skip + log malformed rows | AC-5.1, AC-5.2, AC-5.3, AC-5.4, AC-5.5, AC-5.6 |
| `JobExecutionListener` prints summary | AC-6.1, AC-6.2, AC-6.3 |
| Job triggered on startup | AC-7.2 |
| Testcontainers MySQL in tests | AC-8.1, AC-8.2 |
| Duplicate detection integration test | AC-8.3 |
| Malformed row handling integration test | AC-8.4 |

---

## Open Decisions

| # | Decision | Options | Recommendation |
|---|----------|---------|----------------|
| OD1 | Schema migration tool | Flyway vs. `spring.sql.init` (`schema.sql`) | **Flyway** preferred for production-grade versioning; `schema.sql` acceptable for a demo |
| OD2 | Duplicate detection granularity | Check each row individually (N queries) vs. bulk pre-fetch | Individual JDBC check is simpler and correct for this scale; bulk pre-fetch needed only at high volume |
| OD3 | Summary output mechanism | `JobExecutionListener` writing to logger vs. `System.out` | Use a dedicated `JobExecutionListener` writing via SLF4J at `INFO` level; `System.out.println` is prohibited — per Code Style |
| OD4 | Intra-file duplicate handling (E6) | Sequential check-then-insert per item vs. in-memory set per file | Sequential check-then-insert required in the `ItemWriter` (one item at a time); intra-chunk duplicates are detected correctly since the first insert is visible to subsequent reads within the same transaction |
| OD5 | Performance / expected scale | N/A | No performance requirements; this is a demonstration project. Expected scale: CSV files up to ~10,000 rows |
