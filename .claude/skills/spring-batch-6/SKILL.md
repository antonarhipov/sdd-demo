---
name: spring-batch-6
description: >
  Expert guidance for writing, migrating, and reviewing Spring Batch 6.x code.
  Use this skill whenever the user is working with Spring Batch 6, Spring Boot 4,
  batch jobs, chunk-oriented steps, job repositories, item readers/writers,
  or migrating from Spring Batch 5.x. Trigger on any mention of: Spring Batch,
  @EnableBatchProcessing, JobRepository, ItemReader, ItemWriter, ChunkOrientedStep,
  batch migration, or batch job configuration. Also trigger when the user is
  building CSV importers, ETL pipelines, or scheduled data processing jobs
  in a Spring/Java context.
---

# Spring Batch 6 Skill

This skill provides accurate, up-to-date guidance for Spring Batch **6.0** (GA November 2025),
which ships with Spring Boot 4 and Spring Framework 7. It covers three scenarios:

1. **Writing new batch jobs** from scratch using the 6.x APIs
2. **Migrating** existing 5.x code to 6.x
3. **Reviewing / debugging** batch code for correctness

---

## Quick Reference: What Changed in 6.x

### Dependencies
- Spring Framework **7**, Spring Data **4**, Spring Integration **7**
- Jackson **3.x** (Jackson 2.x deprecated)
- Java **17+** required
- Micrometer **1.16**

### The Three Biggest Breaking Changes

#### 1. `@EnableBatchProcessing` is no longer JDBC-specific
```java
// v5 — JDBC baked in
@EnableBatchProcessing(dataSourceRef = "batchDataSource", taskExecutorRef = "batchTaskExecutor")

// v6 — split into two annotations
@EnableBatchProcessing(taskExecutorRef = "batchTaskExecutor")
@EnableJdbcJobRepository(dataSourceRef = "batchDataSource")
```
For MongoDB: use `@EnableMongoJobRepository` instead.

#### 2. New chunk-oriented step builder API
```java
// v5 (legacy — still compiles but deprecated in 6.x)
new StepBuilder("step", jobRepository)
    .chunk(5, transactionManager)

// v6 (preferred)
new StepBuilder("step", jobRepository)
    .chunk(5)
    .transactionManager(transactionManager)
```
Transaction manager is now **optional** — omit it to use the default.

#### 3. `JobParameter` is now a record; `JobParameters` holds `Set<JobParameter>`
```java
// v5 — name was the map key
Map<String, JobParameter> params = Map.of("date", new JobParameter(today));

// v6 — name lives inside JobParameter
Set<JobParameter> params = Set.of(new JobParameter("date", today));
JobParameters jobParameters = new JobParameters(params);
```

---

## Package Migration Map

**Read `references/package-migration.md`** for the full table of moved types.

Key moves at a glance:

| Type(s) | Old package | New package |
|---|---|---|
| `Job`, `JobExecution`, `JobInstance` | `o.s.b.core` | `o.s.b.core.job` |
| `JobParameter`, `JobParameters`, `RunIdIncrementer` | `o.s.b.core` | `o.s.b.core.job.parameters` |
| `Step`, `StepExecution`, `StepContribution` | `o.s.b.core` | `o.s.b.core.step` |
| `*Listener` interfaces | `o.s.b.core` | `o.s.b.core.listener` |
| `Partitioner`, `PartitionStep` | `o.s.b.core.partition.support` | `o.s.b.core.partition` |
| Jdbc DAOs | `o.s.b.core.repository.dao` | `o.s.b.core.repository.dao.jdbc` |
| Mongo DAOs | `o.s.b.core.repository.dao` | `o.s.b.core.repository.dao.mongo` |
| `JobExplorer` types | `o.s.b.core.explore` | `o.s.b.core.repository.explore` |
| **All infrastructure classes** | `o.s.b.*` | `o.s.b.infrastructure.*` |

---

## Configuration Patterns

### JDBC (most common)
```java
@Configuration
@EnableBatchProcessing
@EnableJdbcJobRepository(dataSourceRef = "dataSource")
public class BatchConfig {

    @Bean
    public Job importJob(JobRepository jobRepository, Step importStep) {
        return new JobBuilder("importJob", jobRepository)
                .start(importStep)
                .build();
    }

    @Bean
    public Step importStep(JobRepository jobRepository,
                           ItemReader<Foo> reader,
                           ItemWriter<Bar> writer) {
        return new StepBuilder("importStep", jobRepository)
                .<Foo, Bar>chunk(100)    // transactionManager optional in v6
                .reader(reader)
                .writer(writer)
                .build();
    }
}
```

### Programmatic (extends class)
```java
// v5: extend DefaultBatchConfiguration
// v6: extend JdbcDefaultBatchConfiguration (or MongoDefaultBatchConfiguration)
public class MyBatchConfig extends JdbcDefaultBatchConfiguration {
    @Override
    protected DataSource getDataSource() { return myDataSource; }
}
```

### Resourceless (no persistence needed)
```java
// DefaultBatchConfiguration now defaults to ResourcelessJobRepository
// No extra dependency needed for simple/test scenarios
@EnableBatchProcessing
public class SimpleBatchConfig { }
```

---

## Domain Model Rules (Immutability)

In v6, the domain model enforces immutability. Important consequences:

- **No default constructors** for readers/writers — all required deps at construction time
- **No orphan entities** — can't create a `JobExecution` without a `JobInstance`
- **No re-assigning IDs** — `Entity.setId()` is gone
- **Entity IDs** changed from `Long` (nullable) to `long` (primitive)
- `JobExplorer` is deprecated — `JobRepository` now **extends** `JobExplorer`
- `JobLauncher` is deprecated — `JobOperator` now **extends** `JobLauncher`

---

## Deprecated / Removed — Avoid in new code

**Deprecated classes to avoid:**
- `ChunkOrientedTasklet`, `SimpleChunkProcessor`, `SimpleChunkProvider` → use new `ChunkOrientedStep`
- `TaskExecutorJobLauncher` → use `JobOperator`
- `JobLauncher` → use `JobOperator`
- `JobExplorer` → use `JobRepository`
- `SimpleStepBuilder`, `FaultTolerantStepBuilder` → use `StepBuilder` directly
- `CommandLineJobRunner` → removed in 6.x

**Removed in 6.0** (were deprecated in 5.x):
- `ChunkListenerSupport`, `JobExecutionListenerSupport`, `SkipListenerSupport`, `StepExecutionListenerSupport`
- `JobRegistryBeanPostProcessor`
- `MongoItemReader` (old one) — use the new `MongoPagingItemReader`
- `Neo4jItemReader`, `Neo4jItemWriter`

**XML namespaces** (`batch:`, `batch-integration:`) are deprecated — prefer Java config.

---

## Database Schema

When migrating an existing database from 5.x → 6.x, run the migration scripts from:
```
spring-batch-core/src/main/resources/org/springframework/batch/core/migration/6.0/
```

Key change: `BATCH_JOB_SEQ` sequence was renamed to `BATCH_JOB_INSTANCE_SEQ`.

---

## Observability

Micrometer's global static registry is no longer used. Define an `ObservationRegistry` bean:
```java
@Bean
public ObservationRegistry observationRegistry(MeterRegistry meterRegistry) {
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    return registry;
}
```

---

## When to Read Reference Files

- **Migrating imports / fixing compile errors** → `references/package-migration.md` (full type-level table)
- **API rename details / renamed methods** → `references/api-changes.md`
- **Writing readers/writers / chunk config** → `references/readers-writers.md`

---

## Common Mistakes to Catch in Code Review

1. Using `@EnableBatchProcessing(dataSourceRef=...)` — must split to `@EnableJdbcJobRepository`
2. Using `.chunk(n, transactionManager)` — migrate to `.chunk(n).transactionManager(...)`
3. Importing `org.springframework.batch.core.Job` → should be `org.springframework.batch.core.job.Job`
4. Importing `org.springframework.batch.core.Step` → should be `org.springframework.batch.core.step.Step`
5. Importing `org.springframework.batch.core.JobParameters` → `org.springframework.batch.core.job.parameters.JobParameters`
6. Using `JobLauncher` directly — prefer `JobOperator`
7. Using `JobExplorer` as a separate bean — `JobRepository` covers it now
8. Creating a `JobParameter` without a name field (v5 style map)
9. Using removed listener support classes (`JobExecutionListenerSupport`, etc.)
10. Referencing `BATCH_JOB_SEQ` in SQL — renamed to `BATCH_JOB_INSTANCE_SEQ`