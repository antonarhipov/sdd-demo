# Spring Batch 6 — API Changes & Renames

Use this file when you encounter deprecated method calls, renamed APIs,
or need to understand the JobLauncher → JobOperator / JobExplorer → JobRepository consolidation.

---

## Table of Contents

1. [JobLauncher → JobOperator](#1-joblauncher--joboperator)
2. [JobExplorer → JobRepository](#2-jobexplorer--jobrepository)
3. [Renamed setters and builder methods](#3-renamed-setters-and-builder-methods)
4. [JobParameters API changes](#4-jobparameters-api-changes)
5. [Chunk API changes](#5-chunk-api-changes)
6. [Deprecated / removed step builders](#6-deprecated--removed-step-builders)
7. [New graceful shutdown](#7-new-graceful-shutdown)
8. [New remote step execution](#8-new-remote-step-execution)
9. [Lambda-style reader/writer configuration (RC2+)](#9-lambda-style-readerwriter-configuration)

---

## 1. JobLauncher → JobOperator

`JobOperator` now **extends** `JobLauncher`. Inject `JobOperator` everywhere.

| v5 | v6 |
|---|---|
| `@Autowired JobLauncher jobLauncher` | `@Autowired JobOperator jobOperator` |
| `jobLauncher.run(job, params)` | `jobOperator.start(job, params)` |
| `TaskExecutorJobLauncher` (class) | Deprecated — configure via `JobOperatorFactoryBean` |

`CommandLineJobRunner` was **removed** in 6.0. Use `JobOperator` from a `@SpringBootApplication` main class or a scheduled task instead.

---

## 2. JobExplorer → JobRepository

`JobRepository` now **extends** `JobExplorer`. Replace all `JobExplorer` injection with `JobRepository`.

| v5 | v6 |
|---|---|
| `@Autowired JobExplorer jobExplorer` | `@Autowired JobRepository jobRepository` |
| `jobExplorer.getJobInstances(...)` | `jobRepository.getJobInstances(...)` |
| `jobExplorer.getJobExecution(id)` | `jobRepository.getJobExecution(id)` |
| `JobExplorerFactoryBean` | `JdbcJobExplorerFactoryBean` (also deprecated — use `JobRepository`) |

---

## 3. Renamed setters and builder methods

### JobStep / JobStepBuilder

| v5 | v6 |
|---|---|
| `JobStep#setJobLauncher(JobLauncher)` | `JobStep#setJobOperator(JobOperator)` |
| `JobStepBuilder#launcher(JobLauncher)` | `JobStepBuilder#operator(JobOperator)` |
| XML: `<step><job job-launcher="..."/>` | XML: `<step><job job-operator="..."/>` |

### SystemCommandTasklet

| v5 | v6 |
|---|---|
| `setJobExplorer(JobExplorer)` | `setJobRepository(JobRepository)` |

### RemoteStepExecutionAggregator

| v5 | v6 |
|---|---|
| constructor `RemoteStepExecutionAggregator(JobExplorer)` | constructor `RemoteStepExecutionAggregator(JobRepository)` |
| `setJobExplorer(JobExplorer)` | `setJobRepository(JobRepository)` |

### Spring Integration gateways

| v5 | v6 |
|---|---|
| `JobLaunchingGateway(JobLauncher)` | `JobLaunchingGateway(JobOperator)` |
| `JobLaunchingMessageHandler(JobLauncher)` | `JobLaunchingMessageHandler(JobOperator)` |
| XML attribute `job-launcher` on `<job-launching-gateway>` | XML attribute `job-operator` |

### spring-batch-integration constructors

| v5 constructor | v6 replacement |
|---|---|
| `RemoteChunkingManagerStepBuilder(String)` | Removed — use the `JobRepository`-accepting constructor |
| `RemotePartitioningManagerStepBuilder(String)` | Removed — use the `JobRepository`-accepting constructor |
| `RemotePartitioningWorkerStepBuilder(String)` | Removed — use the `JobRepository`-accepting constructor |

---

## 4. JobParameters API changes

### JobParameter is now a record

```java
// v5
JobParameter param = new JobParameter(value, type, identifying);
String name = /* had to look it up in the parent JobParameters map */;

// v6 — name is part of the record
JobParameter param = new JobParameter("myParam", value);
String name = param.name(); // available directly
```

### JobParameters holds a Set, not a Map

```java
// v5
Map<String, JobParameter> map = new LinkedHashMap<>();
map.put("date", new JobParameter(LocalDate.now()));
JobParameters params = new JobParameters(map);

// v6
Set<JobParameter> set = new LinkedHashSet<>();
set.add(new JobParameter("date", LocalDate.now()));
JobParameters params = new JobParameters(set);

// or with the builder (unchanged API, updated internally)
JobParameters params = new JobParametersBuilder()
        .addLocalDate("date", LocalDate.now())
        .toJobParameters();
```

### JobParametersIncrementer — user-supplied params are now ignored

In v6, when a job has an incrementer attached, calling `jobOperator.start(job, userSuppliedParams)` will log a warning and ignore the user-supplied params. The incrementer controls the sequence exclusively. If you need custom params alongside an incrementer, remove the incrementer and manage sequencing yourself.

---

## 5. Chunk API changes

### Building steps

```java
// v5 (deprecated in 6.x, compiles but will be removed in v7)
StepBuilder.chunk(int chunkSize, PlatformTransactionManager txManager)
StepBuilder.chunk(CompletionPolicy policy, PlatformTransactionManager txManager)

// v6 (preferred)
StepBuilder.chunk(int chunkSize)              // transactionManager optional
           .transactionManager(txManager)     // set explicitly if needed
```

### Removed Chunk internal methods (were used by legacy chunk model)

These methods on `Chunk` were deprecated and are being removed in 6.x:
- `getSkips()`, `skip(Exception)`, `clearSkips()`, `getSkipsSize()`
- `isEnd()`, `setEnd()`, `isBusy()`, `setBusy(boolean)`
- `getUserData()`, `setUserData(Object)`

The new `ChunkOrientedStep` does not use these; they only apply to the legacy `ChunkOrientedTasklet`.

### ChunkListener annotation-driven approach

```java
// Instead of implementing ChunkListener interface, prefer annotations:
@Component
public class MyChunkListener {
    @BeforeChunk
    public void before(ChunkContext context) { ... }

    @AfterChunk
    public void after(ChunkContext context) { ... }

    @AfterChunkError
    public void onError(ChunkContext context) { ... }
}
```

---

## 6. Deprecated / removed step builders

| Class | Status | Replacement |
|---|---|---|
| `SimpleStepBuilder` | Deprecated | Use `StepBuilder.chunk(...)` directly |
| `FaultTolerantStepBuilder` | Deprecated | Use `StepBuilder.faultTolerant()` directly |
| `ChunkOrientedTasklet` | Deprecated | New `ChunkOrientedStep` (set via `StepBuilder`) |
| `SimpleChunkProcessor` | Deprecated | Handled internally by new chunk model |
| `SimpleChunkProvider` | Deprecated | Handled internally by new chunk model |
| `FaultTolerantChunkProcessor` | Deprecated | Handled internally by new chunk model |
| `FaultTolerantChunkProvider` | Deprecated | Handled internally by new chunk model |
| `ChunkMonitor` | Deprecated | No direct replacement needed |
| `BatchRetryTemplate` | Deprecated | Use Spring Retry directly |

---

## 7. New graceful shutdown

Spring Batch 6 introduces `JobOperator#stop(long executionId)` with proper semantics:

```java
// Signal a running job execution to stop gracefully
jobOperator.stop(executionId);
// The job repository is updated with STOPPED status once the current step finishes
// The job can be restarted from the stopping point
```

`StoppableTasklet#stop()` is **deprecated** — graceful shutdown now flows through the `JobOperator`.

---

## 8. New remote step execution

Remote step execution (execute steps on remote machines) is new in 6.0:

```java
Step remoteStep = new StepBuilder("remoteStep", jobRepository)
        .remoteStep()
        // configuration for remote execution
        .build();
```

Useful for distributing batch workloads across a cluster without partitioning.

---

## 9. Lambda-style reader/writer configuration

Introduced in RC2, now stable in GA. Builders accept contextual lambdas:

```java
// Old builder pattern
var reader = new FlatFileItemReaderBuilder<MyRecord>()
        .resource(resource)
        .delimited()
        .delimiter(",")
        .quoteCharacter('"')
        .names("field1", "field2")
        .targetType(MyRecord.class)
        .build();

// New lambda style
var reader = new FlatFileItemReaderBuilder<MyRecord>()
        .resource(resource)
        .delimited(config -> config
                .delimiter(',')
                .quoteCharacter('"'))
        .names("field1", "field2")
        .targetType(MyRecord.class)
        .build();
```

Both styles work in 6.0; the lambda style is preferred for complex configurations.

---

## Observability — required bean

```java
// v5: global static registry was used automatically
// v6: explicit bean required
@Bean
public ObservationRegistry observationRegistry(MeterRegistry meterRegistry) {
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    return registry;
}
```

Without this bean, batch metrics will **not** be collected.