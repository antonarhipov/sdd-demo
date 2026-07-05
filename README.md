# sdd-demo — Spec-Driven Development, end to end

A worked example of **spec-driven development (SDD)**: instead of prompting a coding
agent straight into implementation, you drive it through a pipeline of small,
composable steps that turn a rough feature request into a validated spec, and only
*then* into code.

This repo is the companion to the blog post
[**Prompting is transient, specs are persistent context**](https://ideaunfiltered.substack.com/p/prompting-is-transient-specs-are-persistent-context).

The thesis, in one line: a prompt evaporates the moment the turn ends; a spec is an
artifact that lives in the repo, gets reviewed, gets versioned, and can be handed to
*any* agent (or human) later. So the interesting work is not crafting the perfect
prompt — it's producing the durable spec that the prompt would have thrown away.

---

## The demo feature

Everything here is built around one deliberately unglamorous feature request
(`spec/proposal.md`):

> Implement a batch job with Spring Batch and JDBC to import temperature data from
> CSV files into a MySQL database — extract `name`, `datetime`, `temp`; treat
> `(name, datetime)` as a unique pair; report and ignore duplicates; print an
> insert/duplicate summary; use Testcontainers (no H2); Java 21-compatible, records
> not POJOs.

Seven lines of intent. The whole point of the demo is to watch those seven lines
turn into a spec with 18 verifiable behaviors, 19 acceptance criteria, 20 technical
rules, a review that catches two real problems, a 5-phase task plan, and finally a
working Spring Batch 6 / Spring Boot 4 application — with every line of code
traceable back to a criterion, and every criterion back to the original request.

---

## Repository layout: read the branches, not just the files

The workflow is encoded in **three branches**, each a snapshot of a different stage.
That's the fastest way to see what SDD actually produces at each step.

| Branch | What it holds | Read it to see… |
|---|---|---|
| **`main`** | The reusable SDD skills only (`.agents/skills/`, `.claude/skills/`). No spec, no app code. | The *method* — the six pipeline steps, portable to any project. |
| **`spec`** | `main` + the generated spec artifacts under `spec/` (`spec.md`, `criteria.md`, `rules.md`, `review.md`, `tasks.yaml`). No implementation yet. | The *persistent context* — everything the agent decided **before** writing a line of code. |
| **`implementation`** | `spec` + the actual code, tests, Flyway migration, `status.md`, and a domain skill (`spring-batch-6`). | The *result* — code produced by executing the plan task by task. |

```
main ──────────────────────────────────────────────► (skills: the method)
  │
  └── spec ─────────────────────────────────────────► (skills + spec/*: the contract)
        │  phase-by-phase execution commits:
        └── implementation ──────────────────────────► (skills + spec/* + src/*: the code)
             35cc791 phase 1
             c5514c6 Phase 2 done  (+ spring-batch-6 skill)
             a8bbdc5 phase 3 complete
             139f0ef phase 4 complete
             27fa025 phase 5
```

To walk the demo:

```bash
git checkout main            # look at .agents/skills — the pipeline definition
git checkout spec            # look at spec/ — the validated spec, no code
git checkout implementation  # look at src/ — the code, with spec/ still present
git log implementation --oneline   # each commit ≈ one phase of the plan
```

> The `.agents/skills/` and `.claude/skills/` directories hold the same skill
> content; the duplication just lets both the generic agent runner and Claude Code
> discover them. `main` predates the `.claude/` mirror by one commit (the tip of
> `main` is a later tweak to skill wording), which is why a diff shows the skills as
> "modified" between `main` and `spec`.

---

## The SDD pipeline

Six skills, run in order. Each consumes the previous step's artifact and produces the
next. Each has an explicit **success criteria** gate and can **route back** to an
earlier step when it finds a gap — the pipeline is a loop, not a straight line.

```
proposal.md
    │
    ▼
┌─ 1. spec ──────┐  Interview one question at a time; resolve every ambiguity.
│  → spec.md     │  Output: numbered Behaviors to verify (B-1 … B-N).
└────────────────┘
    │
    ▼
┌─ 2. criteria ──┐  Translate behaviors into EARS acceptance criteria.
│  → criteria.md │  Output: AC-1 … AC-N, each with a `Covers: B-N` trace.
└────────────────┘
    │
    ▼
┌─ 3. rules ─────┐  Decide the technical design + constraints the agent would
│  → rules.md    │  otherwise invent while coding. Output: RULE-1 … RULE-N (RFC-2119
└────────────────┘  MUST/SHOULD/MAY), each with `Covers: AC-N` + `Reason:`.
    │
    ▼
┌─ 4. review ────┐  Stress-test the whole chain against itself and the codebase.
│  → review.md   │  Output: verdict (PASS / PASS WITH CONDITIONS / FAIL) + Fix Plan.
└────────────────┘
    │
    ▼
┌─ 5. tasks ─────┐  Turn the validated spec into an ordered, AC-traceable plan.
│  → tasks.yaml  │  Output: phases, atomic tasks, checkpoints, coverage map.
└────────────────┘
    │
    ▼
┌─ 6. execute ───┐  Run tasks in order, validate each against its ACs, halt at
│  → src/ + …    │  phase checkpoints for human approval. Maintains status.md.
└────────────────┘
```

The skills live in [`.agents/skills/`](.agents/skills/):

| Step | Skill | Reads | Writes |
|---|---|---|---|
| 1 | [`01-spec`](.agents/skills/01-spec/SKILL.md) | `proposal.md` | `spec.md` |
| 2 | [`02-criteria`](.agents/skills/02-criteria/SKILL.md) | `spec.md` | `criteria.md` |
| 3 | [`03-rules`](.agents/skills/03-rules/SKILL.md) | `spec.md`, `criteria.md` | `rules.md` |
| 4 | [`04-review`](.agents/skills/04-review/SKILL.md) | all of the above | `review.md` |
| 5 | [`05-tasks`](.agents/skills/05-tasks/SKILL.md) | all of the above | `tasks.yaml` |
| 6 | [`06-execute`](.agents/skills/06-execute/SKILL.md) | all of the above | `src/`, `status.md` |

### What makes each step more than a prompt

- **Traceability is enforced, not hoped for.** Every AC cites the B-N it covers;
  every RULE cites the AC; every task cites both. The review step's first job is to
  check that no link in that chain is broken. You can pick any line of code and walk
  it back to a sentence in the original request.
- **Ambiguity is resolved up front, once.** The `spec` step interviews you one
  question at a time and records every decision under *Resolved ambiguities* — so the
  implementing agent never has to guess, and never guesses differently on a re-run.
- **Negative decisions are recorded.** `rules.md` doesn't just say what to use; it
  records what was *declined* and why (e.g. RULE-2: no CSV-parser dependency; RULE-20:
  no custom `SkipPolicy`). The most-often-lost context is "why didn't you use X" — SDD
  keeps it.
- **Review runs before code exists.** In this demo the review returned
  **PASS WITH CONDITIONS** and caught two genuine issues — an under-specified test
  strategy (MAJOR-1) and a missing `(sourceFile, sourceLine)` carrier needed for the
  duplicate WARN logs (MAJOR-2) — both cheap to fix in `rules.md`, expensive to fix in
  code. See [`spec/review.md`](spec/review.md) on the `spec` branch.
- **Execution halts at checkpoints.** The `execute` step stops at the end of every
  phase and waits for `APPROVED` / `REVISE` / `ROLLBACK`. Humans stay in the loop at
  the boundaries that matter, not on every keystroke.

---

## What the pipeline produced for this feature

On the **`spec`** branch, under `spec/`:

- **`spec.md`** — feature summary, 13 resolved ambiguities, explicit assumptions, 11
  handled edge cases, and **B-1 … B-18** (the behavior contract).
- **`criteria.md`** — **AC-1 … AC-19** in [EARS](https://alistairmavin.com/ears/)
  form, plus explicit coverage exclusions (performance, security, UI — considered and
  declined with reasons).
- **`rules.md`** — the design (components under `org.example.sdd.tempimport`, data
  flow, boundaries) and **RULE-1 … RULE-20**, with an AC↔RULE cross-reference table.
- **`review.md`** — verdict, five review categories, five risk hotspots, and a Fix
  Plan sequenced in pipeline order.
- **`tasks.yaml`** — a `walking_skeleton` plan: 5 phases, ~18 tasks, one checkpoint
  per phase, every AC mapped to a task.

On the **`implementation`** branch, under `src/` — the Spring Batch application that
satisfies all 19 criteria, plus:

- **`spec/status.md`** — the execution ledger: every task `COMPLETE`, every phase
  `APPROVED`, with per-task notes on non-obvious decisions.
- **`.agents/skills/spring-batch-6/`** — a *domain* skill added during phase 2, giving
  the agent accurate Spring Batch 6 / Spring Boot 4 API guidance (the 6.x release
  changed enough that the agent needed a reference to code against). SDD skills define
  the process; domain skills feed it current facts.

---

## Running the implementation

Requires **JDK 21+**, **Docker** (for MySQL via Docker Compose and Testcontainers),
and the bundled Maven wrapper. All commands run from the `implementation` branch.

```bash
git checkout implementation
```

**Run the batch job.** Spring Boot's Docker Compose support (`compose.yaml`) starts
MySQL automatically; Flyway creates the schema; the job launches once on boot,
imports every `*.csv` in the input directory, and logs a summary.

```bash
mkdir -p data/input
# drop CSV files with name,datetime,temp columns into data/input/
./mvnw spring-boot:run
```

Processed files move to `data/processed/`, hard-failed files to `data/failed/`
(siblings of the input dir, created on demand). The input directory is configurable
via `app.import.input-dir` (default `./data/input`).

**Run the tests.** Integration tests use Testcontainers `mysql:latest` (no H2), so
Docker must be running.

```bash
./mvnw test
```

---

## Reusing the workflow on your own project

The pipeline is project-agnostic. To apply it elsewhere:

1. Copy [`.agents/skills/`](.agents/skills/) (steps 01–06) into your repo — and/or
   `.claude/skills/` if you use Claude Code.
2. Write your feature request to `spec/proposal.md`.
3. Run the skills in order (`spec` → `criteria` → `rules` → `review` → `tasks` →
   `execute`), letting each write its artifact under `spec/` and honoring the
   route-back gates.
4. Commit `spec/` alongside your code. That's the durable context — the part a prompt
   would have thrown away.

The skills carry no project-specific assumptions; the temperature-import feature is
just the example they happen to be demonstrated on here.
