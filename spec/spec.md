# Temperature CSV Import — Spec

## Feature summary

A Spring Batch job, launched on application startup, scans a configured input directory for `*.csv` files containing temperature readings, extracts the `name`, `datetime`, and `temp` columns by header name, and imports each row into a MySQL table with a unique constraint on `(name, datetime)`. Each CSV is processed as its own step: malformed rows are skipped with a WARN, duplicate rows (whether already present in the DB or seen earlier in the same run) are skipped with a WARN, and the file is moved to `processed/` on success or `failed/` on hard failure. At job completion a single INFO summary is logged with per-file and grand-total counts of inserted, duplicate, and malformed rows. MySQL is provisioned via Docker Compose for runtime and via Testcontainers for integration tests; H2 is not used. Implementation uses Java 21-compatible features and Java records for data carriers.

## Resolved ambiguities

| Decision | Choice | Rationale |
|---|---|---|
| CSV input source | Directory scan: all `*.csv` files in a configured input directory | Matches batch-style operations, lets multiple files be imported in one run |
| Processed file handling | Successfully processed files moved to `processed/`, hard-failed files moved to `failed/` (both siblings of the input directory) | Idempotent re-runs, clear audit trail |
| CSV format | Header row required; columns identified by header name (`name`, `datetime`, `temp`); any column order, extra columns ignored | Robust to real-world CSVs and source-side column additions |
| `datetime` format | ISO-8601 local: `yyyy-MM-dd'T'HH:mm:ss`, parsed as `java.time.LocalDateTime`, stored as MySQL `DATETIME` | Timezone-agnostic; matches the proposal's "datetime" wording |
| `temp` type | Java `double` in record, MySQL `DOUBLE` column | Sufficient precision for sensor readings, lightest viable |
| Duplicate scope | A row is a duplicate if `(name, datetime)` collides with any row already inserted in the current run OR an existing DB row | Single, well-defined notion of duplicate |
| Duplicate insertion strategy | Unique index on `(name, datetime)` + `INSERT IGNORE`; inserts vs duplicates derived from `affected rows` per chunk | Race-safe, concurrent-friendly, no extra round trips |
| Duplicate reporting | Per-row WARN log with `name`, `datetime`, source file, line number, plus aggregated counts in the final summary | Traceable diagnosis without extra artifacts |
| Malformed row handling | Skip the row, WARN with file, line number and reason; count as `malformed`; job still succeeds for the file if at least one row was read | Maximizes useful imports, never silently loses data |
| Per-file failure semantics | Each CSV is processed by its own step; a failing file is moved to `failed/` and the job continues with the remaining files; overall job exits `COMPLETED` (per Spring Batch semantics) and the summary reflects per-file outcomes | Throughput-friendly, isolated blast radius |
| Summary output channel | One multi-line INFO log emitted from a `JobExecutionListener#afterJob`; per-file lines plus a grand total of `inserted`, `duplicates`, `malformed`, `filesProcessed`, `filesFailed` | Idiomatic Spring Batch, easy to assert in tests |
| Job trigger | Run once on application boot via Spring Boot's default `spring.batch.job.enabled=true`; job parameters include a `run.id`/timestamp to keep `JobInstance`s distinct | Matches typical batch container deployments |
| Java version target | Implementation restricted to Java 21-compatible APIs/syntax; `pom.xml` `java.version` stays at `25` (not modified by this feature) | Honors proposal's "Java 21 compatible" without churning build config |

## Explicit assumptions

- The input directory is configured via a Spring property `app.import.input-dir` (default `./data/input`); `processed/` and `failed/` directories are siblings of the input directory and are created on demand if missing.
- File discovery is non-recursive and matches `*.csv` (case-insensitive) sorted lexicographically for deterministic order.
- CSV files are UTF-8 encoded, comma-delimited (`,`), with optional double-quoted fields per RFC 4180; embedded commas inside quoted fields are supported.
- Header row matching is case-insensitive and trims surrounding whitespace; required headers are exactly `name`, `datetime`, `temp`.
- A row is considered malformed when any of: required column missing/blank, `datetime` not parseable as `yyyy-MM-dd'T'HH:mm:ss`, `temp` not parseable as a `double`.
- Target table: `temperature_reading(name VARCHAR(255) NOT NULL, recorded_at DATETIME NOT NULL, temperature DOUBLE NOT NULL, UNIQUE KEY uk_name_recorded_at (name, recorded_at))`, created by a Flyway migration under `db/migration`.
- Chunk size is `1000`; this is an internal default and not a behavior to verify.
- Spring Batch metadata tables live in the same MySQL schema, auto-created by Spring Boot's batch initializer.
- Integration tests use Testcontainers `mysql:latest` via the existing `TestcontainersConfiguration` and `@ServiceConnection`; H2 is not on any classpath or test profile.
- The data model record is `TemperatureReading(String name, LocalDateTime recordedAt, double temperature)`.
- "Inserted" and "duplicate" counters are derived from `INSERT IGNORE` affected-row counts per chunk, with per-row WARN duplicate logs produced by a pre-write check against an in-memory `Set<(name, datetime)>` of keys already inserted/seen this run plus a `SELECT EXISTS` lookup; the WARN log and the counter MUST agree.

## Handled edge cases

- **Empty input directory** — Job completes with `COMPLETED`, summary reports 0 files / 0 rows; no error.
- **CSV with header only, no data rows** — File is moved to `processed/`, counted as 0 inserted / 0 duplicates / 0 malformed.
- **Missing required header in a CSV** — Whole file is treated as a hard failure: moved to `failed/`, the job continues with the next file.
- **Duplicate within the same file** — First occurrence inserted, subsequent occurrences logged at WARN and counted as duplicates (not malformed).
- **Duplicate vs an already-present DB row** — Row logged at WARN with file/line and counted as a duplicate; DB row left unchanged (no upsert).
- **Extra unknown columns in CSV** — Ignored; do not cause the row to be flagged as malformed.
- **Blank/whitespace lines in CSV body** — Skipped silently (not counted as malformed).
- **Mixed-case headers / surrounding whitespace** — Treated as a match (e.g. `Name`, ` DateTime `).
- **Hard IO/DB failure mid-file** — File moved to `failed/`, rows already committed in earlier chunks remain (Spring Batch chunk semantics); job proceeds with the next file.
- **Re-running the job with no new files** — `processed/` files are not re-read; summary reports 0 inserted / 0 duplicates / 0 malformed.
- **File appears mid-scan** — Out of scope; only files present at job start are considered.

## Behaviors to verify

- **B-1** The system discovers all `*.csv` files in the configured input directory (non-recursive, case-insensitive match) at job start and processes them in lexicographic order.
- **B-2** The system reads each CSV using header-based column lookup for `name`, `datetime`, and `temp`, ignoring all other columns and tolerating any column order.
- **B-3** The system parses `datetime` as `LocalDateTime` using the pattern `yyyy-MM-dd'T'HH:mm:ss`, and `temp` as `double`.
- **B-4** The system inserts each valid, non-duplicate row into `temperature_reading(name, recorded_at, temperature)`.
- **B-5** The system enforces a unique constraint on `(name, recorded_at)` so that re-importing identical data does not create duplicates in the DB.
- **B-6** The system counts a row as a duplicate when its `(name, datetime)` matches a row already inserted earlier in the same run OR an existing DB row, and skips its insert.
- **B-7** The system emits a WARN log for every duplicate row including `name`, `datetime`, source file name, and 1-based source line number.
- **B-8** The system skips rows where any required column is missing/blank, `datetime` is unparseable, or `temp` is unparseable, emits a WARN log with file, line number, and reason, and counts them as `malformed`.
- **B-9** The system processes each CSV in its own Spring Batch step so that a failure isolated to one file does not abort the job.
- **B-10** The system moves each successfully processed file to a sibling `processed/` directory.
- **B-11** The system moves each hard-failed file (e.g. missing required header, IO error, mid-file DB failure) to a sibling `failed/` directory and continues with the remaining files.
- **B-12** The system tolerates an empty input directory: the job completes successfully with zero-valued counts and no errors.
- **B-13** The system treats a CSV with only the header row as successful with all counts zero and moves it to `processed/`.
- **B-14** The system treats a CSV missing any required header (`name`, `datetime`, `temp`) as a hard failure and moves it to `failed/` without inserting any of its rows.
- **B-15** The system emits, at job completion, a single INFO summary log produced by a `JobExecutionListener#afterJob` containing per-file counts and grand totals of `inserted`, `duplicates`, `malformed`, `filesProcessed`, and `filesFailed`.
- **B-16** The system creates the `processed/` and `failed/` directories on demand if they do not already exist.
- **B-17** The system runs the import job exactly once per application boot using job parameters that include a `run.id`/timestamp to ensure a fresh `JobInstance` on each launch.
- **B-18** The system uses MySQL exclusively (Docker Compose at runtime, Testcontainers `mysql:latest` for integration tests); H2 is not used.

## Out of scope

- Recursive directory scanning, file-system watching, or polling after job start.
- REST/HTTP or scheduled triggers for the job (boot-time launch only).
- Updating existing DB rows when a duplicate `(name, datetime)` arrives (no upsert).
- Persisting a summary record in the DB or writing a rejects CSV artifact.
- Multi-tenant schemas, timezone-aware `datetime`, or units other than the raw `double` value.
- Restart/skip policy beyond Spring Batch defaults (no custom `SkipPolicy` configuration beyond what's needed for malformed rows).

## External dependencies

None.
