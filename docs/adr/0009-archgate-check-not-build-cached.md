# 0009. `archgateCheck` is not stored in the build cache

Status: accepted

## Context

Rules may read variables through `Environment` and call services outside the build, such as an issue tracker. The answer of such a service can change while the task inputs stay the same, so a result from an earlier run is not evidence for the current one. A stale `[PASS]` is worse than no gate.

Gradle reuses a task result in two ways: `UP-TO-DATE`, from the outputs on disk, and `FROM-CACHE`, from the build cache. The task only knows that a rule read the environment after its rules have run. A veto therefore has to be evaluated after the task action.

A spike on Gradle 9.7.1, the minimum supported version, and 9.9.1 tested `outputs.doNotCacheIf` with a spec that reads a marker file the task action writes. On a clean first run the action wrote the marker that should veto caching, yet Gradle logged `Stored cache entry`, and the next run after `clean` was `FROM-CACHE`. With the marker already on disk from an earlier run and a changed input, Gradle logged `Caching disabled ... 'marker true' satisfied`. So Gradle evaluates the spec before the task action runs. It sees the previous marker, or none, never the one written by the current execution.

## Decision

- `ArchgateCheckTask` is `@DisableCachingByDefault(because = "rules may read external services")` instead of `@CacheableTask`. Its inputs and outputs stay declared, so it stays incremental.
- The task has a second output, `build/reports/archgate/state.properties`, next to the report file. The worker writes `readEnvironment=true` or `readEnvironment=false` after the report, also when the gate fails. It writes `true` when a rule obtained `environment()`, which covers `require`, `optional` and `unavailable`, or when any rule was skipped. It writes nothing when the rules cannot be verified or the report cannot be written.
- `outputs.upToDateWhen` reads that file before execution. The task is `UP-TO-DATE` only when the file exists, is readable and records `readEnvironment=false`. Up-to-date checks run on the outputs of the previous run, so this check does not hit the problem the spike found.
- Variables are read inside the worker at execution time, from system properties and then from environment variables. The switches `archgate.ci`, `archgate.offline` and `archgate.ci.serviceUnavailable` are also read from Gradle properties first, like every other `archgate.*` setting; they reach the worker as task inputs. Nothing reads them at configuration time, so the configuration cache stays reusable when a variable changes.

## Consequences

- `archgateCheck` is never `FROM-CACHE`. After `clean`, or on a fresh CI checkout, the rules run again, even when no rule reads the environment.
- A run in which no rule read the environment is `UP-TO-DATE` next time when the inputs are unchanged. A run in which a rule read the environment executes again next time.
- A missing or unreadable state file counts as "read the environment", so the task executes. The first run after upgrading to this version executes once for that reason.
- If a later Gradle version evaluates `doNotCacheIf` after the task action, caching could return for runs that did not read the environment. That needs a new spike and a new ADR.
