# Technical Design and Constraints: Temperature CSV Import

## Overview

A boot-time Spring Batch job that scans a configured directory for `*.csv` temperature files, imports rows into MySQL with a unique `(name, recorded_at)` constraint, moves each file to `processed/` or `failed/`, and emits a single INFO summary at job completion.

Stack: Java 21-compatible source on Spring Boot 4.0.6 (parent `java.version=25` stays untouched), Spring Batch + `spring-boot-starter-batch-jdbc`, Flyway (`flyway-mysql`), MySQL via `mysql-connector-j` (Docker Compose at runtime through `spring-boot-docker-compose`), Testcontainers `mysql:latest` for integration tests.

Links: [spec/spec.md](spec.md), [spec/criteria.md](criteria.md), [spec/proposal.md](proposal.md).

## Design

**Components** (all under `org.example.sdd.tempimport`):
- `TemperatureImportJobConfig` — defines the `Job`, one `Step` per discovered file, the `JobExecutionListener`, and the `run.id` parameter.
- `CsvFileDiscoverer` — lists `*.csv` files in `app.import.input-dir`, non-recursive, case-insensitive, lexicographically sorted.
- `TemperatureCsvItemReader` — a thin wrapper around `FlatFileItemReader<TemperatureRow>` with a `HeaderAwareLineMapper` that resolves `name`/`datetime`/`temp` columns by header name (any order, extras ignored) and validates required headers.
- `TemperatureRowProcessor` — parses `datetime` and `temp`, classifies each row as `valid`, `malformed`, or `intra-run duplicate` against a per-step `Set<NameRecordedAt>`, and emits the per-row WARN logs.
- `TemperatureReadingWriter` — `INSERT IGNORE` via `JdbcTemplate`, derives `inserted` vs `duplicates` from affected-row counts; cross-run duplicates that survive the in-memory check are logged from the writer.
- `FileDispositionTasklet` — moves the source file to `processed/` or `failed/` based on the step's `ExitStatus`, creating the directory on demand.
- `ImportSummaryListener` — `JobExecutionListener#afterJob`; aggregates per-step counters from `ExecutionContext` and logs the single INFO summary.
- `TemperatureReading` (record) — `(String name, LocalDateTime recordedAt, double temperature)`; `TemperatureRow` (record) — `(String name, String rawDatetime, String rawTemp, String sourceFile, int sourceLine)`, the raw CSV-shape carrier populated by the custom `LineMapper` between reader and processor; `sourceFile` and `sourceLine` are stamped on every emitted row so the processor (AC-8) and the writer (cross-run duplicate branch of RULE-8) can both produce the WARN-log fields mandated by RULE-9.

**Boundaries:** the package exposes only the `@Bean Job` and the `app.import.input-dir` property; parser internals, counters, and SQL stay package-private.

**Flow:** boot → `JobLauncher` runs the job with `run.id=<timestamp>` → `CsvFileDiscoverer` enumerates files → for each file, a dynamically-built `Step` runs `Reader → Processor → Writer` over chunks of 1000, followed by `FileDispositionTasklet` → `ImportSummaryListener#afterJob` logs totals.

**Key dependencies:** no new libraries. `FlatFileItemReader` (already on the classpath) is the CSV parser; Flyway owns schema; `JdbcTemplate` does the inserts. Ecosystem options considered and declined: Apache Commons CSV and Univocity (would add a dependency without solving a problem `FlatFileItemReader` cannot, given a small custom `LineMapper`).

## Codebase Alignment

There is no `AGENTS.md`/`CLAUDE.md`/`GEMINI.md` and no existing source beyond `SddDemoApplication`, `TestcontainersConfiguration`, and an empty `application.properties`. Conventions are therefore inherited from the build: Spring Boot 4.0.6 starter idioms, package root `org.example.sdd`, MySQL + Flyway + Testcontainers already wired in `pom.xml`, and `spring-boot-docker-compose` for runtime DB. The feature places its code under a new `org.example.sdd.tempimport` sub-package and contributes one Flyway migration under `src/main/resources/db/migration`. No deviations from project tooling; the only deliberate diff against typical "Spring Batch CSV" templates is the custom header-by-name `LineMapper` (required by AC-2) rather than a fixed-column tokenizer.

## Rules

### RULE-1
**Covers:** project-wide
**MUST** place all production code for this feature under the package `org.example.sdd.tempimport` (and sub-packages), and **MUST NOT** expose any class outside that package other than the `@Bean Job` and the `@ConfigurationProperties`/`@Value`-bound input-directory property.
**Reason:** Confines batch wiring, SQL, and parsing details so the rest of the application depends only on the job's public surface.

### RULE-2
**Covers:** project-wide
**MUST NOT** add new third-party dependencies (CSV parsers, retry libraries, mapping libraries) to `pom.xml` for this feature.
**Reason:** Ecosystem Survey considered Apache Commons CSV and Univocity Parsers; both were declined because `FlatFileItemReader` plus a small `LineMapper` already satisfies AC-2/AC-3/AC-4 with quoted-field support. Reconsider if multi-source CSV variants or non-trivial dialect handling are added later.

### RULE-3
**Covers:** AC-2, AC-3, AC-4
**MUST** read CSV files with Spring Batch `FlatFileItemReader<TemperatureRow>` configured with a custom `LineMapper` that resolves the `name`, `datetime`, and `temp` columns by header name (case-insensitive, trimmed) on the first non-blank line of each file, supporting any column order and ignoring extra columns.
**Reason:** Locks in the Ecosystem Survey outcome and makes AC-2 validatable by reading the reader configuration.

### RULE-3a
**Covers:** AC-2, AC-7, AC-8
**MUST** make the custom `LineMapper` capture the source file name (no path) and the 1-based source line number of the current record and stamp them onto every emitted `TemperatureRow` as the `sourceFile` and `sourceLine` fields; **MUST** treat the header row as line 1 so that the first data row is line 2; **MUST NOT** drop or recompute these values downstream of the reader.
**Reason:** RULE-9 fixes the duplicate WARN-log shape to `(name, datetime, sourceFile, sourceLine)`; only the reader knows the line number, so the carrier record has to ferry it to both the processor (intra-run duplicates) and the writer (cross-run duplicates surviving the in-memory check).

### RULE-4
**Covers:** AC-3
**MUST** parse the `datetime` column as `java.time.LocalDateTime` using exactly `DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")`; a `DateTimeParseException` **MUST** classify the row as malformed (RULE-10), not abort the step.
**Reason:** Pattern is fixed by AC-3 and the spec; centralising it prevents pattern drift between reader and processor.

### RULE-5
**Covers:** AC-4
**MUST** parse the `temp` column with `Double.parseDouble` after trimming; a `NumberFormatException` **MUST** classify the row as malformed (RULE-10).
**Reason:** Matches the spec's "`temp` type" decision and keeps malformed-row handling uniform.

### RULE-6
**Covers:** AC-5, AC-6
**MUST** persist rows via `JdbcTemplate` using `INSERT IGNORE INTO temperature_reading (name, recorded_at, temperature) VALUES (?, ?, ?)` batched per chunk, and **MUST** derive the `inserted` and `duplicates` counters from the per-row affected-row counts returned by the batch.
**Reason:** Implements the spec's race-safe duplicate strategy; keeps inserts and counters consistent without a separate count query.

### RULE-7
**Covers:** AC-6
**MUST** create the `temperature_reading` table and its `UNIQUE KEY uk_name_recorded_at (name, recorded_at)` via a single Flyway migration file under `src/main/resources/db/migration` named `V1__create_temperature_reading.sql`.
**Reason:** Aligns with the existing `flyway-mysql` dependency; gives the unique constraint a fixed, versioned home.

### RULE-8
**Covers:** AC-7, AC-8
**MUST** detect intra-run duplicates inside the step's `ItemProcessor` by maintaining a `Set<NameRecordedAt>` of keys already accepted in the current step, and **MUST** emit the per-row duplicate WARN before the row is dropped; cross-run duplicates that bypass the set (concurrent inserts) **MUST** still be counted via the writer's affected-row delta and logged with the same WARN shape.
**Reason:** Concretises the spec's "WARN log and counter MUST agree" invariant and pins the AC-8 log contents to the processor.

### RULE-9
**Covers:** AC-8
**MUST** format every duplicate WARN log with exactly these fields, in this order: `name`, `datetime` (ISO-8601), source file name (no path), and 1-based source line number; **MUST** be emitted at `WARN` level via SLF4J on a logger named after the producing class.
**Reason:** Makes AC-8 mechanically verifiable from log output and test assertions.

### RULE-10
**Covers:** AC-9
**MUST** treat a row as malformed when any required column is missing/blank, `datetime` is unparseable, or `temp` is unparseable; the processor **MUST** return `null` (filter the row out of the chunk), increment the step's `malformed` counter, and log one WARN containing `<file>`, line number, and a human-readable reason; **MUST NOT** propagate the exception to abort the step.
**Reason:** Encodes "skip with WARN, never silently lose data" and forbids the easy mistake of letting Spring Batch's default skip policy turn malformed rows into step failures.

### RULE-11
**Covers:** AC-10, AC-12, AC-15
**MUST** build one Spring Batch `Step` per discovered file at job-construction time, wire each step's `ExitStatus` so that a missing required header, an IO error, or a mid-file DB exception ends that step as `FAILED` without throwing out of the job, and **MUST** chain steps so the next file's step always runs regardless of the previous step's outcome.
**Reason:** Implements per-file isolation (AC-10) and the failure-routing precondition for AC-12/AC-15.

### RULE-12
**Covers:** AC-11, AC-12, AC-17
**MUST** move each file with `java.nio.file.Files.move(..., StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)` from a `FileDispositionTasklet` that runs after the chunk step, creating the destination sibling directory (`processed/` on success `ExitStatus`, `failed/` otherwise) via `Files.createDirectories` immediately before the move.
**Reason:** Single, mechanically-checkable file-move path; satisfies AC-11/AC-12/AC-17 in one place rather than scattered across listeners.

### RULE-13
**Covers:** AC-16
**MUST** emit the end-of-job summary from a single `JobExecutionListener#afterJob` implementation, at INFO level, as one log entry whose message contains a per-file line for each step plus a final line with grand totals of `inserted`, `duplicates`, `malformed`, `filesProcessed`, and `filesFailed`; **MUST NOT** emit the summary from any other place.
**Reason:** Pins AC-16's "exactly one INFO log entry" so tests can assert it by capturing a single event.

### RULE-14
**Covers:** AC-18
**MUST** add a `run.id` job parameter set to `System.currentTimeMillis()` (or an equivalent monotonically-fresh value) at every launch, and **MUST** rely on Spring Boot's default `spring.batch.job.enabled=true` plus the auto-configured `JobLauncherApplicationRunner` to launch the job exactly once per boot.
**Reason:** Guarantees a fresh `JobInstance` per boot without inventing a custom runner.

### RULE-15
**Covers:** AC-1, AC-13
**MUST** discover input files with `Files.list(inputDir)`, filter to regular files whose name matches `*.csv` case-insensitively, sort by file name with `Comparator.naturalOrder()`, and **MUST** treat an empty result as a successful job with no steps registered (other than a no-op flow) so the summary listener still emits zero-valued totals.
**Reason:** Makes AC-1's ordering and AC-13's empty-directory tolerance both validatable from the discoverer alone.

### RULE-16
**Covers:** AC-19, project-wide
**MUST NOT** add H2 (`com.h2database:h2`) or any other embedded database to `pom.xml` in any scope, and integration tests **MUST** obtain MySQL through the existing `TestcontainersConfiguration` using `MySQLContainer(DockerImageName.parse("mysql:latest"))` with `@ServiceConnection`.
**Reason:** Locks in AC-19 and the "no H2" invariant at the dependency level, where it is cheapest to enforce.

### RULE-17
**Covers:** project-wide
**MUST** use Java `record` types for all data carriers introduced by this feature (`TemperatureReading`, `TemperatureRow`, the `(name, recordedAt)` key, and the per-file counter aggregate), and **MUST NOT** introduce Lombok or hand-written POJOs for these shapes.
**Reason:** Mirrors the proposal's explicit "records, not POJOs" directive and prevents drift back to mutable beans.

### RULE-18
**Covers:** project-wide
**MUST** restrict source-level Java features used by this feature to those available in Java 21, even though `pom.xml` sets `java.version=25`; **MUST NOT** modify `<java.version>` in `pom.xml`.
**Reason:** Honors the spec's "Java 21-compatible APIs/syntax" decision without churning the build.

### RULE-19
**Covers:** AC-2, AC-7, AC-8, AC-9, AC-10, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, AC-17
**MUST** provide at least one Testcontainers-backed `@SpringBatchTest` integration test per branch listed in this rule (success move, hard-failure move, missing-header failure, empty directory, header-only file, summary log assertion, on-demand directory creation), each using a temp directory as `app.import.input-dir`. In addition, **MUST** provide context-free JUnit unit tests covering:
- the `LineMapper` (RULE-3, RULE-3a): header lookup by name with case-insensitive trimmed match, tolerance of any column order and extra columns, missing-header detection as a hard failure, and correct `sourceFile`/1-based `sourceLine` stamping on every emitted `TemperatureRow` (AC-2, AC-15);
- the `TemperatureRowProcessor` (RULE-8, RULE-9, RULE-10): intra-run duplicate detection against the per-step `Set<NameRecordedAt>` returning `null` for the second occurrence and incrementing the `duplicates` counter (AC-7); malformed-row classification for missing/blank required columns, unparseable `datetime`, and unparseable `temp`, with the `malformed` counter incremented and the processor returning `null` rather than throwing (AC-9); and per-row WARN log assertions (via a log appender or capturing logger) verifying the exact field order `name, datetime, sourceFile, sourceLine` for duplicates (AC-8) and `sourceFile, sourceLine, reason` for malformed rows (AC-9).

Unit tests **MUST NOT** load a Spring context and **MUST NOT** depend on Testcontainers; integration tests **MUST NOT** be used as the only coverage for AC-7, AC-8, or AC-9.
**Reason:** Concretises the testing pyramid for this feature so coverage is checkable from the test sources; pins the row-level event-driven and unwanted-behavior ACs (AC-7/AC-8/AC-9) to fast unit tests where the WARN shape and counters are mechanically assertable, while leaving file-disposition branches on the Testcontainers tier.

### RULE-20
**Covers:** project-wide
**MUST NOT** introduce a custom `SkipPolicy`, `RetryPolicy`, or `Spring Batch Operator/Decider` beyond the minimum needed to route malformed rows (handled by the processor returning `null`) and per-file failures (handled by `Step` `ExitStatus` and the chained `FileDispositionTasklet`).
**Reason:** Surveyed as the canonical "skip/retry" Spring Batch surface; declined because the spec's semantics are already expressible with processor filtering and `ExitStatus` routing, and a custom policy would duplicate that logic.

## Cross-Reference

| AC    | Rules                              |
|-------|------------------------------------|
| AC-1  | RULE-15                            |
| AC-2  | RULE-3, RULE-3a, RULE-19           |
| AC-3  | RULE-3, RULE-4                     |
| AC-4  | RULE-3, RULE-5                     |
| AC-5  | RULE-6                             |
| AC-6  | RULE-6, RULE-7                     |
| AC-7  | RULE-8, RULE-3a, RULE-19           |
| AC-8  | RULE-8, RULE-9, RULE-3a, RULE-19   |
| AC-9  | RULE-10, RULE-19                   |
| AC-10 | RULE-11, RULE-19                   |
| AC-11 | RULE-12, RULE-19                   |
| AC-12 | RULE-11, RULE-12, RULE-19          |
| AC-13 | RULE-15, RULE-19                   |
| AC-14 | RULE-19                            |
| AC-15 | RULE-11, RULE-3a, RULE-19          |
| AC-16 | RULE-13, RULE-19                   |
| AC-17 | RULE-12, RULE-19                   |
| AC-18 | RULE-14                            |
| AC-19 | RULE-16                            |

## Design Exclusions

- **Custom `SkipPolicy` / `RetryPolicy` configuration** — out of scope; the spec accepts Spring Batch defaults plus processor-level filtering (see RULE-20).
- **Multi-source / partitioned / restartable batch with checkpointing** — out of scope; single-directory, single-table import does not justify Spring Batch's partitioning or restart-from-checkpoint features.
- **File-system watching, polling, REST or scheduled triggers** — out of scope per the spec; this feature is boot-time only.
- **Upserts on duplicate keys** — out of scope per the spec; `INSERT IGNORE` is the only write path.
- **Performance budgets / chunk-size tuning** — no measurable target in the spec; chunk size `1000` is an internal default and explicitly not a behavior to verify.
- **Security, authn/authz, PII handling** — feature has no remote surface; no security rules are warranted.
- **Observability beyond logging** — no metrics or tracing rules; the spec only mandates the WARN/INFO log surfaces.

## External Dependencies

None. All judgment calls flagged by the Ecosystem Survey (CSV parser library) have been resolved via Interactive Resolution and locked in by RULE-2 and RULE-3.
