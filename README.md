# SDD Demo — CSV Temperature Data Import

A Spring Boot batch application that imports temperature data from CSV files into a MySQL database. This project serves as a demonstration of **Spec-Driven Development (SDD)** — an approach where AI agent skills guide the entire lifecycle from proposal to working code.

## What It Does

The application reads all `.csv` files from a configured input directory, parses temperature records (`name`, `datetime`, `temp`), detects duplicates against the database, and inserts new records — all orchestrated by Spring Batch.

**Key behaviors:**
- Reads all `*.csv` files from the directory specified by `batch.input.dir`
- Parses ISO 8601 datetimes and decimal temperatures
- Skips and logs malformed rows (missing fields, unparseable values)
- Detects duplicates by checking `(name, datetime)` pairs against the database before insert
- Prints a summary on completion: `Batch complete — status: <STATUS>, inserted: <N>, duplicates: <N>, malformed: <N>`

## Tech Stack

| Technology | Purpose |
|------------|---------|
| Java 21 | Language |
| Spring Boot 4 | Application framework |
| Spring Batch 6 | Batch job orchestration (reader → processor → writer) |
| JDBC (`JdbcTemplate`) | Database access (no JPA/Hibernate) |
| MySQL | Production database |
| Flyway | Schema migration |
| Testcontainers | Integration tests with real MySQL |
| Gradle | Build tool |

## Project Structure

```
src/main/java/org/example/sdd/
├── Application.java                  # Spring Boot entry point
├── model/
│   ├── CsvRow.java                   # Raw CSV row (Java record)
│   └── TemperatureRecord.java        # Validated row (Java record)
├── batch/
│   ├── CsvItemReader.java            # MultiResourceItemReader for CSV files
│   ├── TemperatureItemProcessor.java # Parsing, validation, malformed row handling
│   └── TemperatureItemWriter.java    # JDBC insert with duplicate detection
├── config/
│   └── BatchJobConfig.java           # Job and Step bean definitions
└── listener/
    ├── BatchJobListener.java         # Directory validation + summary report
    └── MalformedRowSkipListener.java # Skip counting
```

## Running the Application

### Prerequisites
- Java 21
- Docker (for MySQL)

### Start MySQL
```bash
docker compose up -d
```

### Run the batch job
```bash
mkdir -p data/input
# Place your CSV files in data/input/
./gradlew bootRun
```

### CSV format
```csv
name,datetime,temp
Station-A,2024-01-15T14:30:00,22.5
Station-B,2024-01-15T15:00:00,18.3
```

Extra columns beyond `name`, `datetime`, and `temp` are silently ignored.

### Run tests
```bash
./gradlew test
```

Tests use Testcontainers to spin up a real MySQL instance — no H2 or in-memory substitutes.

---

## Spec-Driven Development (SDD)

This entire application was built using **Spec-Driven Development** — a workflow where Junie AI agent skills transform a short feature proposal into a complete, validated specification before any code is written.

### The SDD Pipeline

```
Proposal → Requirements → Acceptance Criteria → Technical Constraints → Spec Review → Plan → Implementation
```

Each stage is handled by a dedicated AI agent skill:

| # | Skill | Role | Output |
|---|-------|------|--------|
| 1 | **Requirements Analyst** | Surfaces ambiguities, missing info, implicit assumptions, and edge cases from the proposal | `spec/requirements.md` |
| 2 | **Quality Analyst** | Derives behavioral acceptance criteria using WHEN-THEN-SHALL format | `spec/acceptance_criteria.md` |
| 3 | **Software Architect** | Defines technical constraints — technology choices, component design rules, and patterns | `spec/constraints.md` |
| 4 | **Specification Reviewer** | Reviews the full spec package for gaps, contradictions, and ambiguities | Review feedback |
| 5 | **Plan Generator** | Produces a phased implementation plan with tasks, dependencies, and validation criteria | `spec/plan.yaml` |

After the spec is complete, the `AGENTS.md` execution protocol drives implementation:
- Tasks are executed sequentially within phases
- Each task has explicit acceptance criteria and validation steps
- Phase boundaries are checkpoints requiring human approval
- Progress is tracked in `spec/status.md`

### Why SDD?

- **Front-loads thinking** — ambiguities and edge cases are resolved before writing code, not discovered during debugging
- **Creates a contract** — acceptance criteria and constraints give the AI agent clear, testable goals
- **Enables phased delivery** — human review at checkpoints catches issues early
- **Produces documentation as a side effect** — the spec directory is a complete record of every decision made

### Spec Artifacts

All specification artifacts live in the `spec/` directory:

| File | Contents |
|------|----------|
| `requirements.md` | Resolved ambiguities, assumptions, edge cases, functional requirements |
| `acceptance_criteria.md` | Behavioral specs in WHEN-THEN-SHALL format |
| `constraints.md` | Architecture rules, technology decisions, component design constraints |
| `plan.yaml` | Phased implementation plan with 6 phases and 22 tasks |
| `status.md` | Execution progress — all phases complete |
