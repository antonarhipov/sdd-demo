# Spec Review: Temperature CSV Import

## Summary
- Feature: Temperature CSV Import (boot-time Spring Batch job)
- Verdict: **PASS WITH CONDITIONS**
- Counts: 0 blockers, 2 majors, 1 minor
- Action: apply the Fix Plan (rules step only), then rerun review.

## Discipline Check
Clean. Every `B-N` in `spec.md` (B-1..B-18) appears in at least one AC's `Covers:` line. Every `AC-N` in `criteria.md` (AC-1..AC-19) appears in the rules.md Cross-Reference table or is justified under `Coverage exclusions`. Every `Covers: AC-N` in `rules.md` points to an existing AC. Spot-checked rows: AC-2→RULE-3, AC-13→RULE-15+RULE-19, AC-19→RULE-16 all match the rule bodies. All 19 ACs use a recognizable EARS template (Ubiquitous for AC-6/AC-19, Event-driven `When ...` for AC-1/AC-2/AC-3/AC-4/AC-5/AC-8/AC-11/AC-13/AC-14/AC-16/AC-18, Unwanted-behavior `If ..., then ...` for AC-7/AC-9/AC-12/AC-15/AC-17, State-driven for AC-10).

## Conflicts
None. Cross-checked AC↔AC, AC↔RULE, RULE↔RULE, Design↔Rules, scope re-entry, and negative-decision violations. Notable consistency points:
- RULE-2 (no new deps incl. CSV parsers) is respected by RULE-3 (uses already-classpath `FlatFileItemReader`).
- RULE-16 (no H2) is respected by `pom.xml` (no `com.h2database:h2`).
- RULE-20 (no custom `SkipPolicy`/`RetryPolicy`) is respected by RULE-10 (malformed → `null` from processor) and RULE-11 (`ExitStatus` routing).
- "Out of scope" items in `spec.md` (upserts, recursive scan, REST/scheduled triggers, file-watching) do not resurface in `criteria.md` or `rules.md`.

## Codebase Grounding
Design fits the current codebase. `pom.xml` already declares every library `rules.md` relies on: `spring-boot-starter-batch`, `spring-boot-starter-batch-jdbc`, `spring-boot-starter-flyway`, `flyway-mysql`, `mysql-connector-j`, `spring-boot-docker-compose`, plus `spring-boot-starter-batch-test`, `spring-boot-testcontainers`, `testcontainers-mysql`. `TestcontainersConfiguration` already exposes `MySQLContainer(DockerImageName.parse("mysql:latest"))` with `@ServiceConnection` exactly as RULE-16 prescribes. The proposed `org.example.sdd.tempimport` sub-package and `src/main/resources/db/migration/V1__create_temperature_reading.sql` migration are creatable without colliding with existing sources (only `SddDemoApplication` is present). Spring Boot parent `4.0.6` and `java.version=25` confirmed; RULE-18's "source restricted to Java 21" is achievable without build changes.

## EARS ↔ Test Strategy
RULE-19 names integration-test branches for the file-disposition surface (success move, hard-failure move, missing-header failure, empty directory, header-only file, summary log assertion, on-demand directory creation) — these map cleanly to AC-10..AC-17 (state/unwanted-behavior patterns) and AC-16 (event-driven). However, the row-level Event-driven and Unwanted-behavior ACs around parsing and duplicate detection (AC-2..AC-4, AC-7, AC-8, AC-9) are not on RULE-19's mandatory list, and unit coverage of the processor/line-mapper is left as `MAY`. See `MAJOR-1` below.

## Risk Hotspots
1. **`INSERT IGNORE` affected-row counts under JDBC batching** — area: RULE-6 derives `inserted` vs `duplicates` from per-row batch return codes. Reason: MySQL Connector/J's `executeBatch` commonly returns `SUCCESS_NO_INFO` (-2) for batched statements, so the per-row delta can be unobservable in chunked writes. Mitigation: either disable JDBC batching for the writer (per-row executes), enable `rewriteBatchedStatements=false` and inspect `Statement.getUpdateCount()`, or pre-check via a chunk-local `SELECT ... IN (?, ?, ...)` and only count post-DB collisions as the rare "cross-run race" case.
2. **Source `(file, line)` propagation reader → processor → writer** — area: AC-8 and RULE-9 require the writer's cross-run duplicate WARN to carry source file name and 1-based line number, but `FlatFileItemReader` does not put the line number on the mapped item by default. Mitigation: make `TemperatureRow` a 5-field record `(name, datetime, temp, sourceFile, sourceLine)` populated inside the custom `LineMapper`; see `MAJOR-2` for the structural fix.
3. **Dynamic step-per-file at `@Bean Job` construction** — area: RULE-11 and Design require building one `Step` per discovered file at job-construction time, but Spring Boot constructs the `Job` bean once at startup. Reason: ties file discovery into bean wiring and makes integration tests fight `JobLauncherApplicationRunner` for control of when discovery happens. Mitigation: define a `JobFactory` invoked by a custom `ApplicationRunner` (or override the auto-configured runner) that runs `CsvFileDiscoverer` per launch and assembles a `FlowJob`, instead of registering the file-shaped steps statically.
4. **Unpinned `mysql:latest` Testcontainers image** — area: RULE-16 / AC-19 mandate `mysql:latest`. Reason: floating tag yields non-reproducible CI and silent MySQL version drift, which can move `INSERT IGNORE` semantics. Mitigation: out of scope to change (AC-19 explicitly says `mysql:latest`); flag for the implementing agent to pin during CI hardening as a follow-up rather than re-litigating the AC.
5. **Boot-time job firing inside Spring tests** — area: RULE-14 keeps `spring.batch.job.enabled=true`. Reason: any `@SpringBootTest` that loads the full context will launch the import job and touch the configured input dir, which may not exist or may collide with the test's own setup. Mitigation: have RULE-19 tests pin `spring.batch.job.enabled=false` in their `@TestPropertySource` and drive the job via `JobLauncherTestUtils` (already provided by `spring-boot-starter-batch-test`).

## Fix Plan

Execution order: rules → review

### rules (rerun rules)
1. `[localized]` MAJOR-1: Tighten RULE-19 (or add a sibling rule) so that AC-7, AC-8, and AC-9 are mandatorily covered — at minimum require unit tests for the processor (intra-run duplicate detection, malformed-row classification, WARN-log fields) and the line mapper (header lookup, missing-header detection). Keep the Testcontainers branches list as today; add the unit-level coverage as a `MUST`, not a `MAY`. No downstream artifacts depend on RULE-19's wording, so this stays inside `rules.md`.
2. `[cascades]` MAJOR-2: Update the Design section's `TemperatureRow` description and add/extend a rule so that `TemperatureRow` is the record `(String name, String rawDatetime, String rawTemp, String sourceFile, int sourceLine)` (or equivalent) populated inside the custom `LineMapper`, and so that RULE-9's WARN-shape obligation is satisfiable from both the processor (AC-8) and the writer (RULE-8's cross-run duplicate branch). Strengthen RULE-3 or add `RULE-3a` requiring the `LineMapper` to capture and stamp the current line number onto each emitted row.
   Downstream effect: the Cross-Reference table is unchanged (still AC-2/AC-3/AC-4/AC-7/AC-8), but the Design paragraph and one or two rule bodies move; no change to `spec.md` or `criteria.md` is required.

### review
Rerun once the rules-step fixes are in.
