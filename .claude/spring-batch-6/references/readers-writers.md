# Spring Batch 6 — Readers & Writers Reference

Use this file when writing or reviewing item reader/writer configuration.
All classes are now under `org.springframework.batch.infrastructure.*`.

---

## Table of Contents

1. [Common flat file readers](#1-flat-file-readers)
2. [Database readers](#2-database-readers)
3. [JSON readers/writers](#3-json)
4. [XML readers/writers](#4-xml)
5. [Database writers](#5-database-writers)
6. [Mongo readers/writers](#6-mongodb)
7. [Composite patterns](#7-composite-patterns)
8. [Custom reader/writer tips](#8-custom-readerwriter-tips)

---

## 1. Flat File Readers

```java
// CSV reader — v6, with new lambda config style
@Bean
public FlatFileItemReader<Employee> reader(Resource resource) {
    return new FlatFileItemReaderBuilder<Employee>()
            .name("employeeReader")
            .resource(resource)
            .delimited(cfg -> cfg.delimiter(',').quoteCharacter('"'))
            .names("firstName", "lastName", "salary")
            .targetType(Employee.class)
            .build();
}

// Fixed-width reader
@Bean
public FlatFileItemReader<Employee> fixedWidthReader(Resource resource) {
    return new FlatFileItemReaderBuilder<Employee>()
            .name("fixedWidthReader")
            .resource(resource)
            .fixedLength(cfg -> cfg
                    .columns(new Range(1, 10), new Range(11, 25), new Range(26, 35))
                    .names("firstName", "lastName", "salary"))
            .targetType(Employee.class)
            .build();
}
```

**Writer:**
```java
@Bean
public FlatFileItemWriter<Employee> writer(Resource output) {
    return new FlatFileItemWriterBuilder<Employee>()
            .name("employeeWriter")
            .resource(output)
            .delimited(cfg -> cfg.delimiter(","))
            .names("firstName", "lastName", "salary")
            .build();
}
```

---

## 2. Database Readers

### JDBC Cursor (streaming, single-threaded)
```java
@Bean
public JdbcCursorItemReader<Employee> cursorReader(DataSource dataSource) {
    return new JdbcCursorItemReaderBuilder<Employee>()
            .name("cursorReader")
            .dataSource(dataSource)
            .sql("SELECT first_name, last_name, salary FROM employees WHERE active = true")
            .rowMapper(new BeanPropertyRowMapper<>(Employee.class))
            .build();
}
```

### JDBC Paging (restartable, multi-threaded safe)
```java
@Bean
public JdbcPagingItemReader<Employee> pagingReader(DataSource dataSource,
                                                    PagingQueryProvider queryProvider) {
    return new JdbcPagingItemReaderBuilder<Employee>()
            .name("pagingReader")
            .dataSource(dataSource)
            .queryProvider(queryProvider)
            .pageSize(100)
            .rowMapper(new BeanPropertyRowMapper<>(Employee.class))
            .build();
}

@Bean
public SqlPagingQueryProviderFactoryBean queryProvider(DataSource dataSource) {
    SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
    factory.setDataSource(dataSource);
    factory.setSelectClause("SELECT first_name, last_name, salary");
    factory.setFromClause("FROM employees");
    factory.setWhereClause("WHERE active = true");
    factory.setSortKey("id");
    return factory;
}
```

---

## 3. JSON

**Note:** Spring Batch 6 uses **Jackson 3.x** by default. Jackson 2.x is deprecated.

```java
// Jackson 3 ObjectMapper
ObjectMapper mapper = new ObjectMapper();  // Jackson 3

@Bean
public JsonItemReader<Employee> jsonReader(Resource resource) {
    return new JsonItemReaderBuilder<Employee>()
            .name("jsonReader")
            .resource(resource)
            .jsonObjectReader(new JacksonJsonObjectReader<>(Employee.class))
            .build();
}

@Bean
public JsonFileItemWriter<Employee> jsonWriter(Resource output) {
    return new JsonFileItemWriterBuilder<Employee>()
            .name("jsonWriter")
            .resource(output)
            .jsonObjectMarshaller(new JacksonJsonObjectMarshaller<>())
            .build();
}
```

---

## 4. XML

```java
@Bean
public StaxEventItemReader<Employee> xmlReader(Resource resource) {
    return new StaxEventItemReaderBuilder<Employee>()
            .name("xmlReader")
            .resource(resource)
            .addFragmentRootElements("employee")
            .unmarshaller(oxmMarshaller())
            .build();
}
```

---

## 5. Database Writers

### JdbcBatchItemWriter
```java
@Bean
public JdbcBatchItemWriter<Employee> jdbcWriter(DataSource dataSource) {
    return new JdbcBatchItemWriterBuilder<Employee>()
            .dataSource(dataSource)
            .sql("INSERT INTO employees (first_name, last_name, salary) " +
                 "VALUES (:firstName, :lastName, :salary)")
            .beanMapped()
            .build();
}
```

### JPA Writer
```java
@Bean
public JpaItemWriter<Employee> jpaWriter(EntityManagerFactory emf) {
    return new JpaItemWriterBuilder<Employee>()
            .entityManagerFactory(emf)
            .build();
}
```

---

## 6. MongoDB

```java
// v6 — use MongoPagingItemReader (old MongoItemReader was removed)
@Bean
public MongoPagingItemReader<Employee> mongoReader(MongoTemplate mongoTemplate) {
    return new MongoPagingItemReaderBuilder<Employee>()
            .name("mongoReader")
            .template(mongoTemplate)
            .collection("employees")
            .targetType(Employee.class)
            .jsonQuery("{active: true}")
            .sorts(Map.of("_id", Sort.Direction.ASC))
            .pageSize(100)
            .build();
}

@Bean
public MongoItemWriter<Employee> mongoWriter(MongoTemplate mongoTemplate) {
    return new MongoItemWriterBuilder<Employee>()
            .template(mongoTemplate)
            .collection("employees")
            .build();
}
```

---

## 7. Composite Patterns

### CompositeItemWriter (write to multiple destinations)
```java
@Bean
public CompositeItemWriter<Employee> compositeWriter(
        JdbcBatchItemWriter<Employee> jdbcWriter,
        MongoItemWriter<Employee> mongoWriter) {
    return new CompositeItemWriterBuilder<Employee>()
            .delegates(jdbcWriter, mongoWriter)
            .build();
}
```

### ClassifierCompositeItemWriter (route by type)
```java
@Bean
public ClassifierCompositeItemWriter<Object> routingWriter(...) {
    ClassifierCompositeItemWriter<Object> writer = new ClassifierCompositeItemWriter<>();
    writer.setClassifier(item -> item instanceof Employee ? empWriter : deptWriter);
    return writer;
}
```

### MultiResourceItemWriter (one file per chunk)
```java
@Bean
public MultiResourceItemWriter<Employee> multiWriter(
        FlatFileItemWriter<Employee> delegate) {
    return new MultiResourceItemWriterBuilder<Employee>()
            .name("multiWriter")
            .resource(new FileSystemResource("output/employees-"))
            .resourceSuffixCreator(index -> index + ".csv")
            .itemCountLimitPerResource(500)
            .delegate(delegate)
            .build();
}
```

---

## 8. Custom Reader/Writer Tips

### v6 immutability requirement
```java
// WRONG in v6 — default constructor + setters pattern
public class MyReader implements ItemReader<Foo> {
    private DataSource dataSource;
    public void setDataSource(DataSource ds) { this.dataSource = ds; }
    public void afterPropertiesSet() { ... }
}

// CORRECT in v6 — all deps at construction time
public class MyReader implements ItemReader<Foo> {
    private final DataSource dataSource;
    public MyReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
```

### ItemStreamReader for restartability
```java
public class MyReader implements ItemStreamReader<Foo> {
    @Override
    public void open(ExecutionContext executionContext) { /* restore state */ }

    @Override
    public void update(ExecutionContext executionContext) { /* save state */ }

    @Override
    public void close() { /* cleanup */ }

    @Override
    public Foo read() throws Exception { /* return null at end of data */ }
}
```

### Thread-safety note
- `JdbcCursorItemReader` is **not** thread-safe — use with single-threaded steps or synchronize
- `JdbcPagingItemReader` is thread-safe — use for multi-threaded steps
- `FlatFileItemReader` is **not** thread-safe — wrap with `SynchronizedItemStreamReader` for multi-threaded steps:

```java
@Bean
public SynchronizedItemStreamReader<Employee> synchronizedReader(
        FlatFileItemReader<Employee> reader) {
    return new SynchronizedItemStreamReaderBuilder<Employee>()
            .delegate(reader)
            .build();
}
```