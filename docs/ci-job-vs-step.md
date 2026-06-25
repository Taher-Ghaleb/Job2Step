# GitHub Actions: Job-Level vs Step-Level Considerations

This document summarizes what lives at the **job** level in a multi-job CI pipeline, and what to rethink when merging into **one job with multiple steps**. It is based on experiments with the `Job2Step` repository workflow (Build → Unit Tests → Integration Tests).

## Things That Change the Most

### 1. `needs` (job dependencies)

**Before (separate jobs):** `unit-tests` waits for `build`; `integration-tests` waits for `unit-tests`.

```yaml
unit-tests:
  needs: build

integration-tests:
  needs: unit-tests
```

**After (one job):** Step order replaces `needs`. Steps run sequentially: compile → unit tests → integration tests.

**Consider:** You lose parallel jobs. If `build` and something else were independent, merging forces them to run one after another.

---

### 2. `if` conditions

**Before (job level):** A job can reference other jobs:

```yaml
if: needs.unit-tests.result == 'success' && ...
```

**After (step level):** Use step-level status functions:

```yaml
if: success() && ...        # prior steps passed
if: failure() && ...        # run only if something failed (e.g. upload logs)
if: always() && ...         # run regardless of prior step outcome
```

**Consider:** `needs.*.result` no longer exists for internal steps — only `success()`, `failure()`, `cancelled()`, `always()`, and `steps.<id>.outcome`.

**Example from this repo (integration job condition):**

```yaml
if: needs.unit-tests.result == 'success' && (contains(fromJSON('["main", "master"]'), github.ref_name) || (github.event_name == 'pull_request' && contains(fromJSON('["main", "master"]'), github.base_ref)))
```

**Equivalent on a step after merging:**

```yaml
if: success() && (contains(fromJSON('["main", "master"]'), github.ref_name) || (github.event_name == 'pull_request' && contains(fromJSON('["main", "master"]'), github.base_ref)))
```

---

### 3. Job outputs (`outputs` + `needs`)

**Before:** One job exports data; another consumes it:

```yaml
jobs:
  build:
    outputs:
      version: ${{ steps.meta.outputs.version }}
    steps:
      - id: meta
        run: echo "version=1.0" >> $GITHUB_OUTPUT

  deploy:
    needs: build
    steps:
      - run: echo ${{ needs.build.outputs.version }}
```

**After:** Use step outputs in the same job:

```yaml
steps:
  - id: build
    run: echo "version=1.0" >> $GITHUB_OUTPUT

  - run: echo ${{ steps.build.outputs.version }}
```

**Consider:** Anything passed between jobs via artifacts or outputs must become step outputs or stay on disk in the same runner workspace.

---

### 4. `runs-on` (runner OS / labels)

**Before:** Each job can use a different runner:

```yaml
build:
  runs-on: ubuntu-latest

integration-tests:
  runs-on: windows-latest
```

**After:** One `runs-on` for the whole job.

**Consider:** If jobs targeted different OSes or self-hosted labels, you cannot merge them into one job without giving up that split.

---

### 5. `strategy.matrix`

**Before:** A job runs many times in parallel (e.g. Java 17 + 21):

```yaml
strategy:
  matrix:
    java: [17, 21]
```

**After:** Matrix still works, but it applies to the **entire** job — all steps run for each matrix value. You cannot matrix only the integration step without extra scripting or keeping a separate job.

---

### 6. `container` and `services`

**Before:** Job-level Docker container or service containers (Postgres, Redis):

```yaml
services:
  postgres:
    image: postgres:16
```

**After:** Container and services apply to the **whole** job, not individual steps.

**Consider:** If only integration tests needed a database, merging means compile/unit tests also run inside that container context — or you keep integration as its own job.

---

### 7. Artifacts (`upload-artifact` / `download-artifact`)

**Before:** Job A uploads `target/`; Job B downloads it.

**After:** The workspace persists between steps on the same runner — usually **no artifact upload/download needed** for build output.

**Consider:** Artifacts are still useful if you want to retain build output after the job ends, or share with other jobs/workflows.

---

### 8. Caching (`actions/cache`, `setup-java` cache)

**Before:** Each job is a fresh VM — cache must be restored every job.

**After:** Maven `target/`, local repo, and compiled classes stay on disk between steps.

**Consider:** You may only need checkout + `setup-java` once; less cache churn, faster overall.

---

### 9. `environment` (deployments / approvals)

Works at **both** job and step level:

```yaml
environment: production
```

**Before:** Often on a deploy job only.

**After:** Put `environment` on the specific deploy step if only that step should trigger protection rules.

**Consider:** If the whole merged job has `environment: production`, even compile/test steps are tied to that environment.

---

### 10. `timeout-minutes` and `continue-on-error`

| Setting | Job level | Step level |
|--------|-----------|------------|
| `timeout-minutes` | Whole job budget | Per-step budget |
| `continue-on-error` | Job can fail but workflow continues | Step fails; later steps can still run with `if: success() \|\| failure()` |

**Consider:** With one job, a failed unit-test step fails the entire job unless you use `continue-on-error: true` and handle it explicitly in later `if` conditions.

---

### 11. `permissions`

Usually set per job (or workflow):

```yaml
permissions:
  contents: read
```

**After:** One permission block applies to all steps.

**Consider:** If only one job needed elevated permissions (e.g. `packages: write`), merging grants that to every step in the job.

---

### 12. `concurrency`

```yaml
concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true
```

Works at workflow or **job** level — not on individual steps.

**After:** Still fine at the single job level; behavior is the same for the whole pipeline.

---

### 13. Branch protection / required checks

**Before:** Three job names → three separate checks in GitHub:

- `Build`
- `Unit Tests`
- `Integration Tests`

**After:** One check: `Build and Test`.

**Consider:** If branch protection requires specific job names, merging breaks that until you update required checks.

---

### 14. Skipped vs failed semantics

**Before:** If `integration-tests` is skipped via `if: false`, dependent jobs see `skipped` (not `failure`).

**After:** A skipped step is skipped; the job can still be **green** if prior steps passed.

**Consider:** Integration `if` on a separate job vs on a step behaves slightly differently in the Actions UI and in downstream `needs` logic.

---

## Quick Decision Guide

| Keep as separate job when… | Safe to merge into steps when… |
|---------------------------|-------------------------------|
| Different OS / runner labels | Same runner for everything |
| Needs DB/services/container | Tests are plain `mvn` on JDK |
| Matrix only for one phase | Same config for all phases |
| Separate required status checks | One combined check is OK |
| Parallel independent work | Strict sequential pipeline |
| Deploy approvals / `environment` only for deploy | Env gate on one step is enough |

---

## This Repository: Job-Level vs Step-Level Mapping

When the `Job2Step` workflow was merged from three jobs into one job, these were the main translations:

| Job-level (3 jobs) | Step-level (1 job) |
|--------------------|---------------------|
| `needs: build` | Step order after compile |
| `needs: unit-tests` | Run integration after unit test step |
| `needs.unit-tests.result == 'success'` | `success()` on integration step |
| Branch `if` on integration job | Same `if` on integration step |
| 3× checkout + JDK setup | 1× checkout + JDK setup |

### Three-job layout (current)

```
Build → Unit Tests → Integration Tests (conditional)
```

- Each job runs on its own runner.
- Each job repeats checkout and JDK setup.
- Jobs are chained with `needs`.
- Integration tests use a job-level `if` condition.

### Single-job layout (experimented)

```
Checkout → JDK → Compile → Unit Tests → Integration Tests (conditional step)
```

- One runner for the full pipeline.
- Workspace and Maven cache persist between steps.
- Step order replaces `needs`.
- Integration condition moves to the step level.

---

## Trade-offs Summary

**Separate jobs (current approach):**

- Clear separation in the Actions UI.
- Each phase can be a required status check.
- Jobs can use different runners, matrices, containers, or environments.
- More overhead: repeated checkout, JDK setup, and cache restore.

**Single job with steps:**

- Faster for simple sequential pipelines.
- Simpler YAML for basic build/test flows.
- No parallel execution across former jobs.
- Job-level features must be re-evaluated at step level (see sections above).
