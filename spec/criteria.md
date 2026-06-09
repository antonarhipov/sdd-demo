# Acceptance Criteria: Temperature CSV Import

## Functional

### AC-1: Discover CSV files in input directory
**Covers:** B-1

When the import job starts, the system shall discover every file in the configured input directory whose name matches `*.csv` case-insensitively at the top level only, and process them in ascending lexicographic order by file name.

### AC-2: Header-based column lookup
**Covers:** B-2

When reading a CSV file, the system shall locate the `name`, `datetime`, and `temp` columns by header name matched case-insensitively after trimming surrounding whitespace, accept any column order, and ignore all other columns.

### AC-3: Parse `datetime` as `LocalDateTime`
**Covers:** B-3

When parsing a CSV row, the system shall parse the `datetime` value as a `LocalDateTime` using the pattern `yyyy-MM-dd'T'HH:mm:ss`.

### AC-4: Parse `temp` as `double`
**Covers:** B-3

When parsing a CSV row, the system shall parse the `temp` value as a Java `double`.

### AC-5: Insert valid rows
**Covers:** B-4

When a CSV row is valid and is not a duplicate, the system shall insert exactly one record into `temperature_reading` with `name`, `recorded_at`, and `temperature` taken from the row's `name`, `datetime`, and `temp` values respectively.

### AC-6: Enforce unique constraint on `(name, recorded_at)`
**Covers:** B-5

The system shall enforce a unique constraint on `(name, recorded_at)` in `temperature_reading` such that re-importing identical rows does not create additional database rows.

### AC-7: Detect cross-run and intra-run duplicates
**Covers:** B-6

If a CSV row's `(name, datetime)` matches a row already inserted earlier in the same run or an existing row in `temperature_reading`, then the system shall skip the insert for that row and count it as a duplicate.

### AC-8: Per-row duplicate WARN log
**Covers:** B-7

When the system skips a row as a duplicate, it shall emit one WARN log entry containing the row's `name`, the row's `datetime`, the source file name, and the 1-based source line number of the row.

### AC-9: Skip malformed rows with WARN
**Covers:** B-8

If a CSV row has a missing or blank required column, an unparseable `datetime`, or an unparseable `temp`, then the system shall skip the row, emit one WARN log entry containing the source file name, the 1-based line number, and the reason, count the row as `malformed`, and continue processing the remaining rows in the same file.

### AC-10: One Spring Batch step per CSV file
**Covers:** B-9

The system shall process each discovered CSV file in its own Spring Batch step such that a hard failure isolated to one file does not abort the processing of the remaining files in the same run.

### AC-11: Move successful files to `processed/`
**Covers:** B-10

When all rows of a CSV file have been processed without a hard failure for that file, the system shall move the file to a `processed/` directory located as a sibling of the configured input directory.

### AC-12: Move hard-failed files to `failed/`
**Covers:** B-11

If a CSV file encounters a hard failure (including a missing required header, an IO error, or a mid-file database failure), then the system shall move that file to a `failed/` directory located as a sibling of the configured input directory and continue processing the remaining files in the same run.

### AC-13: Tolerate an empty input directory
**Covers:** B-12

When the configured input directory contains no `*.csv` files at job start, the system shall complete the job successfully with `inserted`, `duplicates`, `malformed`, `filesProcessed`, and `filesFailed` all reported as zero and no ERROR log emitted.

### AC-14: Header-only CSV counts as success
**Covers:** B-13

When a CSV file contains a valid header row and no data rows, the system shall complete its step successfully with zero `inserted`, zero `duplicates`, and zero `malformed` for that file, and move the file to `processed/`.

### AC-15: Missing required header is a hard failure
**Covers:** B-14

If a CSV file is missing any of the required headers `name`, `datetime`, or `temp`, then the system shall treat the whole file as a hard failure, insert none of its rows into `temperature_reading`, and move the file to `failed/`.

### AC-16: Single INFO summary log at job completion
**Covers:** B-15

When the import job completes, the system shall emit, from a `JobExecutionListener#afterJob` hook, exactly one INFO log entry that contains per-file counts and grand totals of `inserted`, `duplicates`, `malformed`, `filesProcessed`, and `filesFailed`.

### AC-17: Create `processed/` and `failed/` on demand
**Covers:** B-16

If the `processed/` or `failed/` sibling directory does not exist at the moment the system needs to move a file into it, then the system shall create that directory before performing the move.

### AC-18: One job run per application boot
**Covers:** B-17

When the application boots, the system shall launch the import job exactly once with job parameters that include a `run.id` or timestamp value sufficient to produce a fresh `JobInstance` on every launch.

## Non-functional

### AC-19: MySQL-only persistence
**Covers:** B-18

The system shall use MySQL for all persistence at application runtime and shall use Testcontainers `mysql:latest` for integration tests, with H2 absent from every classpath and test profile.

## Coverage exclusions

- **Performance / throughput / latency:** The spec sets no measurable performance targets; chunk size (`1000`) is declared an internal default and explicitly not a behavior to verify.
- **Security / authorization:** The feature exposes no authenticated or authorized surface (boot-time batch only); no negative authz criteria are applicable.
- **Accessibility / UI:** The feature has no user interface.
- **Java language version target:** "Java 21-compatible APIs/syntax" is a build/source-level constraint, not an observable runtime behavior, so no runtime AC is written; enforcement belongs to the build configuration.
- **Mid-scan file appearance:** Explicitly declared out of scope in the spec ("only files present at job start are considered").
- **Upserts on duplicate keys, recursive scanning, REST/scheduled triggers, rejects-CSV artifacts, summary DB persistence, multi-tenant schemas, timezone-aware datetimes:** Explicitly listed as out of scope in the spec.
