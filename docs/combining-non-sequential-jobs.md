# Combining Non-Sequential Jobs in GitHub Actions

This document discusses whether there is motivation or rationale for combining **non-sequential** jobs in GitHub Actions — jobs that do not depend on each other via `needs` and therefore run in parallel.

## What “Non-Sequential Jobs” Means

In GitHub Actions, non-sequential jobs are jobs with **no `needs` dependency** between them. They start at the same time and run in parallel, for example:

```yaml
jobs:
  lint:
    runs-on: ubuntu-latest
    steps: [...]

  unit-tests:
    runs-on: ubuntu-latest
    steps: [...]

  security-scan:
    runs-on: ubuntu-latest
    steps: [...]
```

None of these waits for another. That is different from a sequential pipeline such as:

```
Build → Unit Tests → Integration Tests
```

where each job explicitly depends on the previous one.

## Is There Rationale for Combining Non-Sequential Jobs?

**Sometimes yes, but less often than for sequential jobs.**

If jobs are truly independent, keeping them separate is usually better because you get:

- Parallelism
- Faster total wall-clock time
- Isolated failure domains
- Separate required checks in branch protection

So the default answer is: **do not combine independent jobs unless you have a specific reason.**

## Motivations That Can Justify Combining Them

### 1. Shared Expensive Setup

If every parallel job repeats the same costly work:

- checkout
- dependency install
- Docker image pull
- database seed
- compiler/toolchain bootstrap

Then one job with shared setup followed by multiple commands can be cheaper than three fresh runners.

Example motivation:

- 3 jobs each spend 2 minutes installing dependencies and 30 seconds doing work
- 1 job installs once, then runs lint/test/scan serially or via a script

This is a **cost/time trade-off**: less runner startup overhead, but less parallelism.

### 2. Shared Filesystem/State

Separate jobs cannot share workspace files unless you use artifacts or cache.

Combine when later tasks need local outputs from earlier tasks that are not worth uploading:

- generated code
- compiled classes
- local Maven/NPM cache
- downloaded test fixtures

In a Java/Maven project, merging can mean `target/` and the Maven cache stay on disk between steps instead of being re-created on each runner.

### 3. Simpler Developer Experience

Teams sometimes merge jobs because:

- one status check is easier to reason about
- one log stream is easier to debug
- branch protection only needs one required check
- there is less YAML duplication

This is mostly **workflow ergonomics**, not technical necessity.

### 4. Permission and Secret Structure

If several small jobs all need the same elevated permissions or secrets, one job can:

- fetch secrets once
- run multiple checks under one permission block

Note: merging can also **expand** exposure if a privileged job now includes low-risk steps too.

### 5. Concurrency Control

If you want only one pipeline instance per branch and all related work to cancel together, a single job can be simpler to manage under one `concurrency` group.

Concurrency works fine across multiple jobs too, so this is a weak reason by itself.

### 6. Resource Constraints

On self-hosted runners or limited parallel capacity, five parallel jobs may queue anyway. In that case, combining work into fewer jobs can reduce queue contention.

### 7. “Fan-In” Orchestration Without True Dependency

Sometimes jobs are logically independent but you still want one final reporting or deploy job. That does **not** mean combining them; it means:

```yaml
report:
  needs: [lint, unit-tests, security-scan]
```

That preserves parallelism and gives one downstream consumer. This is usually better than merging unrelated jobs.

## When Combining Non-Sequential Jobs Is a Bad Idea

Avoid merging if the jobs differ in:

| Dimension | Why keep separate |
|----------|-------------------|
| OS/runner | Different `runs-on` |
| Matrix | Java 17 vs 21, browser matrix, etc. |
| Containers/services | Only integration needs Postgres |
| Runtime | One is 2 min, another is 20 min |
| Failure tolerance | Want lint failure without blocking security scan |
| Required checks | Need distinct pass/fail signals |
| Blast radius | One flaky job should not invalidate everything |

## Better Patterns Than “Combine Unrelated Jobs”

If the concern is duplication, these are usually better than merging:

### 1. Reusable Workflow

```yaml
jobs:
  unit-tests:
    uses: ./.github/workflows/test.yml

  integration-tests:
    uses: ./.github/workflows/integration.yml
```

### 2. Composite Action for Shared Setup

```yaml
steps:
  - uses: ./.github/actions/java-setup
```

### 3. Parallel Jobs + Artifact/Cache Only Where Needed

Keep jobs independent, share only what must be shared.

### 4. Fan-In Job for Reporting

```yaml
ci-summary:
  needs: [lint, unit-tests, security-scan]
  if: always()
```

## Practical Rule of Thumb

| Situation | Recommendation |
|----------|------------------|
| Jobs are independent and similar cost | Keep separate, run in parallel |
| Jobs are independent but repeat heavy setup | Consider combining or extract shared setup |
| Jobs must share local build output | Combine, or use artifacts/cache |
| Jobs need different environments/runners | Keep separate |
| Team wants one required check | Combine or add a summary job |
| One job is strictly after another | Sequential `needs` or merged steps both work |

## For the Job2Step Repository

The current `Job2Step` workflow is **sequential**, not non-sequential:

- `build` must happen before tests
- unit tests should pass before integration tests

So merging makes sense mainly for:

- speed
- less repeated checkout/JDK setup
- simpler logs

There is **little rationale** for combining unrelated jobs in this repo because it does not have parallel independent jobs such as lint + unit tests + security scan running side by side.

## Bottom Line

Yes, there is motivation to combine non-sequential jobs, but usually only when:

1. **Setup cost dominates**
2. **Shared workspace matters**
3. **You want one status/check/log stream**
4. **Runner capacity makes parallelism ineffective**

If the jobs are genuinely independent and similarly lightweight, keeping them parallel is almost always the better design.

## Related Documentation

See also [ci-job-vs-step.md](./ci-job-vs-step.md) for job-level vs step-level considerations when merging **sequential** jobs.
