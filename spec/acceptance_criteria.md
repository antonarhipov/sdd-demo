# Acceptance Criteria

**Pipeline position:** proposal → requirements → *acceptance criteria* → technical constraints → spec review → tasks

**Source:** `spec/requirements.md`

---

## Category 1: CSV File Discovery

**AC-1.1 — Configured directory is read**
WHEN the application starts
THEN the batch job reads the directory path from `batch.input.dir` in `application.properties`
SHALL process all `.csv` files found in that directory

**AC-1.2 — Multiple files processed**
WHEN the configured input directory contains more than one `.csv` file
THEN the batch job processes each file
SHALL import records from all files and aggregate counts in a single summary

**AC-1.3 — No CSV files present**
WHEN the configured input directory exists but contains no `.csv` files
THEN the batch job runs to completion
SHALL report 0 inserted, 0 duplicates, 0 malformed rows, and SHALL log a warning

**AC-1.4 — Configured directory does not exist**
WHEN the configured input directory path does not exist on the filesystem
THEN the application attempts to start the batch job
SHALL fail with a clear error message identifying the missing directory and SHALL NOT proceed with processing

---

## Category 2: CSV Parsing

**AC-2.1 — Required columns extracted**
WHEN a CSV file contains a header row and data rows with `name`, `datetime`, and `temp` columns
THEN the batch job reads each data row
SHALL extract only the `name`, `datetime`, and `temp` values and ignore all other columns

**AC-2.2 — Extra columns ignored**
WHEN a CSV row contains columns beyond `name`, `datetime`, and `temp`
THEN the parser encounters those extra columns
SHALL not cause an error and SHALL not affect the extracted values

**AC-2.3 — `datetime` parsed as ISO 8601**
WHEN a CSV row contains a `datetime` value in ISO 8601 format (e.g., `2024-01-15T14:30:00`)
THEN the parser processes that row
SHALL convert the value to a `LocalDateTime` and include the record for insertion

**AC-2.4 — `temp` parsed as decimal**
WHEN a CSV row contains a numeric `temp` value (e.g., `23.5`)
THEN the parser processes that row
SHALL represent the value with one decimal place precision

---

## Category 3: Duplicate Detection

**AC-3.1 — Duplicate against existing DB record**
WHEN a CSV row has a `(name, datetime)` pair that already exists in the database
THEN the batch job attempts to process that record
SHALL skip the insert, log the duplicate, and increment the duplicate counter

**AC-3.2 — Unique record inserted**
WHEN a CSV row has a `(name, datetime)` pair not present in the database
THEN the batch job processes that record
SHALL insert it into the database and increment the inserted counter

**AC-3.3 — Intra-file duplicates**
WHEN two rows within the same CSV file share the same `(name, datetime)` pair
THEN the batch job processes both rows
SHALL insert the first occurrence and treat the second as a duplicate (skip, log, count)

**AC-3.4 — Uniqueness key is composite**
WHEN two rows share the same `name` but have different `datetime` values
THEN the batch job processes both rows
SHALL insert both records without treating either as a duplicate

---

## Category 4: Data Insertion

**AC-4.1 — Record persisted to database**
WHEN a non-duplicate, well-formed record is processed
THEN the batch job inserts it into the database
SHALL be retrievable after the job completes with `name`, `datetime`, and `temp` values intact

**AC-4.2 — Database schema present**
WHEN the application starts for the first time against an empty database
THEN the application initializes
SHALL create the required table with columns `name` (VARCHAR(255)), `datetime` (DATETIME), and `temp` (DECIMAL(5,1)) before any records are inserted

**AC-4.3 — DB connection failure**
WHEN the database becomes unavailable during a batch insert
THEN the batch job encounters a connection error
SHALL fail the job, record the failure in Spring Batch metadata tables, and SHALL NOT silently discard data

---

## Category 5: Error Handling — Malformed Rows

**AC-5.1 — Non-ISO 8601 `datetime` skipped**
WHEN a CSV row contains a `datetime` value that does not conform to ISO 8601
THEN the parser encounters that row
SHALL skip the row, log a message identifying the row and the invalid value, and continue processing remaining rows

**AC-5.2 — Non-numeric `temp` skipped**
WHEN a CSV row contains a `temp` value that cannot be parsed as a number (e.g., `"N/A"`)
THEN the parser encounters that row
SHALL skip the row, log a message identifying the row and the invalid value, and continue processing remaining rows

**AC-5.3 — Missing required field skipped**
WHEN a CSV row is missing one or more of the `name`, `datetime`, or `temp` fields
THEN the parser encounters that row
SHALL skip the row, log a message identifying the row and the missing field(s), and continue processing remaining rows

**AC-5.4 — Empty CSV file**
WHEN a CSV file contains only a header row and no data rows
THEN the batch job processes that file
SHALL complete with 0 records inserted from that file and SHALL NOT error

**AC-5.5 — CSV file with no header row**
WHEN a CSV file contains no header row
THEN the batch job attempts to parse it
SHALL treat all rows as malformed, log each one, and record them in the malformed counter

**AC-5.6 — Malformed rows do not halt processing**
WHEN one or more malformed rows appear among valid rows in a CSV file
THEN the batch job encounters each malformed row
SHALL skip each malformed row individually, continue processing all remaining rows, and include a count of skipped rows in the final summary

---

## Category 6: Summary Report

**AC-6.1 — Summary printed on completion**
WHEN the batch job completes successfully
THEN the job finishes execution
SHALL print to the console the total number of records inserted, total duplicates detected and skipped, and total malformed rows skipped

**AC-6.2 — Summary covers all files**
WHEN the input directory contains multiple CSV files
THEN the batch job processes all files
SHALL report aggregated totals across all files in a single summary output

**AC-6.3 — Zero counts reported accurately**
WHEN all records in all CSV files are valid and non-duplicate
THEN the batch job completes
SHALL report 0 duplicates and 0 malformed rows in the summary

---

## Category 7: Language & Framework Constraints

**AC-7.1 — Java records used for data model**
WHEN a parsed CSV row is represented in memory
THEN the data model is instantiated
SHALL be a Java record (not a class with getters/setters)

**AC-7.2 — Job triggered on startup**
WHEN the Spring Boot application starts
THEN the application context is fully initialized
SHALL automatically trigger the batch job without requiring an external HTTP call or manual invocation

---

## Category 8: Testing

**AC-8.1 — Integration tests use real MySQL**
WHEN integration tests for the batch job are executed
THEN the test suite runs
SHALL connect to a real MySQL instance provisioned by Testcontainers

**AC-8.2 — H2 not used**
WHEN any test in the project is executed
THEN the test runtime is initialized
SHALL NOT use an H2 in-memory database as a substitute for MySQL

**AC-8.3 — Duplicate detection tested**
WHEN integration tests run the batch job against pre-seeded database records
THEN the job processes CSV rows matching existing records
SHALL verify those rows are counted as duplicates and not inserted again

**AC-8.4 — Malformed row handling tested**
WHEN integration tests supply a CSV file containing malformed rows
THEN the batch job processes the file
SHALL verify malformed rows are skipped, logged, and counted correctly without aborting the job
