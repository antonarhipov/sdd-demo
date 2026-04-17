# Spring Batch 6 — Full Package Migration Reference

Use this file when fixing import statements or diagnosing compile errors after upgrading from 5.x to 6.x.

---

## Table of Contents

1. [spring-batch-infrastructure module re-root](#1-spring-batch-infrastructure-module-re-root)
2. [Core domain: Job types](#2-core-domain-job-types)
3. [Core domain: Job Parameters](#3-core-domain-job-parameters)
4. [Core domain: Step types](#4-core-domain-step-types)
5. [Listeners](#5-listeners)
6. [Repository / Explorer](#6-repository--explorer)
7. [Repository DAOs — JDBC](#7-repository-daos--jdbc)
8. [Repository DAOs — MongoDB](#8-repository-daos--mongodb)
9. [Partitioning](#9-partitioning)
10. [Renamed classes](#10-renamed-classes)
11. [Database sequence rename](#11-database-sequence-rename)

---

## 1. spring-batch-infrastructure module re-root

This is the broadest change. **Every class** in `spring-batch-infrastructure` moved:

| Old root | New root |
|---|---|
| `org.springframework.batch.item.*` | `org.springframework.batch.infrastructure.item.*` |
| `org.springframework.batch.support.*` | `org.springframework.batch.infrastructure.support.*` |
| `org.springframework.batch.repeat.*` | `org.springframework.batch.infrastructure.repeat.*` |
| `org.springframework.batch.classify.*` | `org.springframework.batch.infrastructure.classify.*` |

This affects **all item readers and writers**, e.g.:
- `org.springframework.batch.item.file.FlatFileItemReader` → `org.springframework.batch.infrastructure.item.file.FlatFileItemReader`
- `org.springframework.batch.item.database.JdbcCursorItemReader` → `org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader`
- `org.springframework.batch.item.json.JsonItemReader` → `org.springframework.batch.infrastructure.item.json.JsonItemReader`

> **Rule of thumb:** if your import starts with `org.springframework.batch.item`, `org.springframework.batch.support`, or `org.springframework.batch.repeat`, prepend `infrastructure.` after `batch.`.

---

## 2. Core domain: Job types

| Class | Old package | New package |
|---|---|---|
| `Job` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `JobExecution` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `JobExecutionException` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `JobInstance` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `JobInterruptedException` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `JobKeyGenerator` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `DefaultJobKeyGenerator` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `StartLimitExceededException` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |
| `UnexpectedJobExecutionException` | `org.springframework.batch.core` | `org.springframework.batch.core.job` |

---

## 3. Core domain: Job Parameters

| Class | Old package | New package |
|---|---|---|
| `JobParameter` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `JobParameters` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `JobParametersBuilder` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `JobParametersIncrementer` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `JobParametersValidator` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `CompositeJobParametersValidator` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `DefaultJobParametersValidator` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `InvalidJobParametersException` | `org.springframework.batch.core` | `org.springframework.batch.core.job.parameters` |
| `RunIdIncrementer` | `org.springframework.batch.core.job.launch.support` | `org.springframework.batch.core.job.parameters` |
| `DataFieldMaxValueJobParametersIncrementer` | `org.springframework.batch.core.job.launch.support` | `org.springframework.batch.core.job.parameters` |

---

## 4. Core domain: Step types

| Class | Old package | New package |
|---|---|---|
| `Step` | `org.springframework.batch.core` | `org.springframework.batch.core.step` |
| `StepContribution` | `org.springframework.batch.core` | `org.springframework.batch.core.step` |
| `StepExecution` | `org.springframework.batch.core` | `org.springframework.batch.core.step` |

---

## 5. Listeners

All listener **interfaces** moved from the core root into a dedicated package:

| Interface | Old package | New package |
|---|---|---|
| `ChunkListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `ItemProcessListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `ItemReadListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `ItemWriteListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `JobExecutionListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `SkipListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `StepExecutionListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |
| `StepListener` | `org.springframework.batch.core` | `org.springframework.batch.core.listener` |

> **Note:** The `*ListenerSupport` adapter classes (`JobExecutionListenerSupport`, etc.) were **removed** in 6.0. Implement the interface directly or use `@BeforeJob` / `@AfterJob` annotations on a `@Bean`.

---

## 6. Repository / Explorer

| Class / Interface | Old package | New package |
|---|---|---|
| `JobExplorer` (interface) | `org.springframework.batch.core.explore` | `org.springframework.batch.core.repository.explore` |
| All `JobExplorer`-related support classes | `org.springframework.batch.core.explore.support` | `org.springframework.batch.core.repository.explore.support` |

> **Important:** `JobExplorer` is **deprecated** in v6. `JobRepository` now extends `JobExplorer`. Inject `JobRepository` wherever you previously injected `JobExplorer`.

---

## 7. Repository DAOs — JDBC

| Class | Old package | New package |
|---|---|---|
| `JdbcExecutionContextDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.jdbc` |
| `JdbcJobExecutionDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.jdbc` |
| `JdbcJobInstanceDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.jdbc` |
| `JdbcStepExecutionDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.jdbc` |

---

## 8. Repository DAOs — MongoDB

| Class | Old package | New package |
|---|---|---|
| `MongoExecutionContextDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.mongo` |
| `MongoJobExecutionDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.mongo` |
| `MongoJobInstanceDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.mongo` |
| `MongoStepExecutionDao` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.mongo` |
| `MongoSequenceIncrementer` | `org.springframework.batch.core.repository.dao` | `org.springframework.batch.core.repository.dao.mongo` |

---

## 9. Partitioning

| Class / Interface | Old package | New package |
|---|---|---|
| `Partitioner` | `org.springframework.batch.core.partition.support` | `org.springframework.batch.core.partition` |
| `PartitionNameProvider` | `org.springframework.batch.core.partition.support` | `org.springframework.batch.core.partition` |
| `PartitionStep` | `org.springframework.batch.core.partition.support` | `org.springframework.batch.core.partition` |
| `StepExecutionAggregator` | `org.springframework.batch.core.partition.support` | `org.springframework.batch.core.partition` |

---

## 10. Renamed classes

| Old fully-qualified name | New fully-qualified name |
|---|---|
| `o.s.b.core.repository.support.JobRepositoryFactoryBean` | `o.s.b.core.repository.support.JdbcJobRepositoryFactoryBean` |
| `o.s.b.core.repository.explore.support.JobExplorerFactoryBean` | `o.s.b.core.repository.explore.support.JdbcJobExplorerFactoryBean` |
| `o.s.b.integration.chunk.ChunkHandler` | `o.s.b.integration.chunk.ChunkRequestHandler` |

---

## 11. Database sequence rename

| Old name | New name |
|---|---|
| `BATCH_JOB_SEQ` | `BATCH_JOB_INSTANCE_SEQ` |

Run migration SQL from: `spring-batch-core/src/main/resources/org/springframework/batch/core/migration/6.0/`