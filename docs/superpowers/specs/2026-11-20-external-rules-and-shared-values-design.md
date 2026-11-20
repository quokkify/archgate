# External rules, shared values and grouped violations — design

Status: draft, awaiting approval. Delete this file once the work it describes is merged; lasting decisions go into ADRs.

## Intent

Rule authors need three things the engine cannot do today:

1. **Rules that call an external service** (an issue tracker, a test-management system). Such a rule needs credentials from outside the build and must behave sensibly when they are absent: skip on a developer machine, fail closed in CI.
2. **One expensive load shared by several rules.** Five rules that check against the same remote index must load it once per run, and the report must show that load once instead of inflating every rule's time.
3. **Violations grouped by reason**, so a rule with several kinds of findings reports them under separate headers.

On top of these, a documented convention, `archgate.changedFiles`, lets such rules restrict themselves to the files a pull request changed.

The behaviour and the report text are already proven in a private Maven-only predecessor of this engine. This design carries them over to the multi-backend, Maven + Gradle engine. The text of the report stays the same wherever the two can share it.

## Goals

- `AnalysisContext.environment()` with `require(names...)`, `optional(name)` and `unavailable(service, cause)`.
- A third rule outcome, skipped (`[SKIP]`), produced only by `ExternalDependencyUnavailable`.
- CI detection, `archgate.ci`, `archgate.offline`, `archgate.ci.serviceUnavailable=fail|skip`, with identical behaviour in both plugins.
- `AnalysisContext.shared(name, build)`: once per run, shared across backends, timed separately, and reported as a `Shared:` line.
- `ViolationGroup` and `ArchitectureRule.checkViolationGroups(...)`, reported as `Violations (N) "<title>":`.
- Report blocks: a rule row and its detail lines form one block, joined with `System.lineSeparator()`, and logged as one entry, so only the row carries the build tool's level prefix.
- The `archgate.changedFiles` convention documented. The engine does not interpret it.

## Non-goals

- No built-in rule that calls an external service. The feature is SPI-only.
- No per-rule configuration in a build file. Contract 11 still holds; `archgate.*` switches are properties only, with no plugin parameter and no `ArchgateExtension` property.
- No retries, timeouts or HTTP client in the engine. Rules own their clients.
- No reading of `archgate.changedFiles` by the engine.

## Anti-goals (a failure even if it "works")

- A cached or `UP-TO-DATE` Gradle result for a run whose rules read the environment. A stale `[PASS]` from yesterday's service state is worse than no gate.
- Any path by which a missing variable or a down service in CI turns into a pass, except the explicit, visible `archgate.ci.serviceUnavailable=skip`.
- Maven and Gradle printing different reports for the same fixture.
- Anything employer-specific in code, docs, fixtures or commit messages (contract 14).

## Constraints

- `api` gets no new dependencies. `core` stays analysis-library-free and build-tool-free (contract 4).
- Gradle: configuration-cache safe. Variables and CI detection are read at execution time inside the worker, never at configuration time. The worker uses `classLoaderIsolation` and runs in the daemon, so `System.getProperty` sees `gradle -DNAME=value` and `System.getenv` sees the client's environment.
- `Archgate-Api-Version`: a rule jar that calls the new methods is built against the new `api`, and the existing `ApiCompatibilityCheck` already fails fast on an older engine. The docs say which release introduced each method.
- Report text is an output contract (contract 9): every new line is pinned by fixture expectations in the same change.

## Approaches considered

How a rule reaches `environment()` and `shared()` when `verify(C)` receives a backend-specific context:

| Approach | Trade-off | Verdict |
|---|---|---|
| **Default methods on `AnalysisContext`**, backed by run services that core passes to every `ContextProvider` | Rule code reads the same as in the predecessor (`ctx.environment()`, `ctx.shared(...)`). Every built-in context must delegate, and a third-party context that does not gets an actionable `UnableToVerifyError` | **Chosen** by the owner |
| Second overload `verify(C, GateServices)` on `ArchitectureRule` | Contexts stay untouched, but a rule that needs services overrides one method and leaves `verify(C)` empty | Rejected |
| Static access through a run-scoped `ScopedValue` | No SPI change, but hidden global state that makes rules harder to test | Rejected |

## Design

### API (`engine/api`)

- `Environment` (interface): `Map<String,String> require(String... names)`, `default Optional<String> optional(String name)`, `default ExternalDependencyUnavailable unavailable(String service, Throwable cause)`. Unset, empty or blank counts as missing. Null or blank names → `IllegalArgumentException`.
- `ExternalDependencyUnavailable extends Error` with `Reason {MISSING_VARIABLES, OFFLINE, SERVICE_UNAVAILABLE}`, factories `missingVariables(List)`, `offline()`, `serviceUnavailable(service, cause)`, and accessors `reason()`, `missingVariables()`, `service()`. Messages:
  - `Missing A, B. Pass them as -DNAME=value or as environment variables.`
  - `External rules are switched off by archgate.offline=true.`
  - `<service> is unavailable: <cause message or cause class name>`
- `ViolationGroup(String title, List<String> violations)` record. `ArchitectureViolation` gains `static grouped(String expectedContract, List<ViolationGroup> groups)`: the message is the contract followed, for every non-empty group, by `Violations (N) "<title>":` and one `    - <violation>` line per violation, all joined with `System.lineSeparator()`. Empty groups are omitted. The flat constructor and its `Violations (N):` format are unchanged, so existing callers see nothing new. `ArchitectureRule.checkViolationGroups(String expectedContract, List<ViolationGroup> groups)` throws only when some group is non-empty.
- `AnalysisContext` gains:
  - `default Environment environment()`, which throws `UnableToVerifyError` naming the context class and saying it must be updated to provide the run services
  - `default <T> T shared(String name, Supplier<T> build)`, with the same fallback
- Run services reach contexts through a new api type (working name `RunServices`, exposing `environment()` and `shared(...)`) obtained from `ProjectLayout` (`default RunServices services()`, which throws the same actionable error). `SimpleProjectLayout` carries the real one. All three `Directory*Context` classes implement `environment()` and `shared()` by delegating to their layout's services.

### Core (`engine/core`)

- `VariableEnvironment(Function<String,String> lookup, boolean offline)`: `require` throws `offline()` first, then `missingVariables` listing every missing name at once. `optional` ignores `offline`. The class also records whether any rule called `require` or `optional`; the Gradle caching rule below depends on that.
- `ExternalPolicy.detect(lookup)`: CI when `archgate.ci` is `true`/`false` (case-insensitive, trimmed; any other value → `UnableToVerifyError`), otherwise when `CI` is non-blank and not `false`. **Builder's call, flagged:** the predecessor also checked `GITLAB_CI` first. GitLab sets `CI` too, so `CI` alone covers it; the source text then reads `(CI is set)`. All three switches are parsed eagerly when the gate starts, so an invalid value fails the build locally and in CI even when no rule reads the environment (the predecessor behaves the same way). Also parsed here: `archgate.offline` (true/false), which is ignored in CI with a warning line, and `archgate.ci.serviceUnavailable` (`fail` by default, or `skip`; anything else → `Unknown archgate.ci.serviceUnavailable value '<v>'. Use fail or skip.`).
- `RunServices` implementation: a `ConcurrentHashMap<String, FutureTask<Object>>`. The first caller builds and the others wait. A throwable is cached and rethrown to every caller, so a down service is asked once. Build time is recorded per name. Re-entrant use of the same name on the same thread → `UnableToVerifyError`. All time spent inside `shared(...)` is subtracted from the calling rule's own duration, for the rule that builds the value as well as for those that wait; the build time appears only on the `Shared:` line. "Once per run" means once per gate execution. A gate already aggregates every selected module (`ModuleDirs`), so a multi-module build with one gate module loads a shared value once; the docs say so.
- `GateRunner`: catches `ExternalDependencyUnavailable` from a rule. Outside CI → skipped outcome. In CI → `UnableToVerifyError("Rule '<name>' cannot run in CI (<source>): <message>")`, except a `SERVICE_UNAVAILABLE` reason with `serviceUnavailable=skip`, which gives a skipped outcome. A skipped rule never fails the gate, whatever `failOn` says.
- `RuleOutcome`: a new external-skip state, kept apart from the existing `skipped` set of empty scopes, which is a different concept (`[PASS]` with a note). `GateResult` carries the shared build times and a flag `readEnvironment`.
- `ReportFormatter`:
  - `format` returns **blocks**. Each block is a rule row plus its detail lines joined with `System.lineSeparator()`; header, footer and summary lines are one-line blocks.
  - Order of rows: passed, then `[SKIP]`, then findings.
  - A skip row is followed by the message and `Skipped because this is not a CI run (<source>); in CI this fails the build.` or, in CI, `Skipped in CI because the service is unavailable (archgate.ci.serviceUnavailable=skip); the rule checked nothing.`
  - The verdict line ends with `, N skipped`. A report with any skip is logged at WARNING at least.
  - `Shared: <name> <ms> ms` comes after the class-import line, only when something was shared.
  - Skips change only the report text, the verdict suffix and the log level. The exit state, the report file format and `ArchgateElements` stay as they are (builder's call).
  - Grouped violations print as `Violations (N) "<title>":`.

### Plugins

- Lookup order, first non-blank wins. Maven: session user properties, then system properties, then environment variables. Gradle: system properties of the build JVM (`gradle -D…`), then environment variables. The lookup is built inside the worker or mojo at execution time.
- Both plugins log each report block as one entry.
- Both plugins print the `archgate.offline is ignored in CI` warning.
- **Gradle caching.** The task writes `readEnvironment` into its output. `outputs.upToDateWhen`, which checks the previous output before execution, returns false when that output records `readEnvironment`, so a run whose rules read the environment is never `UP-TO-DATE` next time. For the build cache, PR 1 starts with a spike on the minimum supported Gradle (9.7.1) and on the current one. The spike checks whether `outputs.doNotCacheIf` can veto storing a result based on state the task set during execution.
  - If it can, `archgateCheck` stays `@CacheableTask` and refuses to store such runs. Runs that never read the environment keep `FROM-CACHE`.
  - If it cannot, `archgateCheck` is no longer stored in the build cache at all (`@DisableCachingByDefault` with a reason) and keeps only the `UP-TO-DATE` behaviour above. `docs/configuration.md` and an ADR record the decision.
  - Decided by the owner. Either way, `CachingFunctionalTest` pins the result.

### Tests

- Unit tests in `api` and `core` for every message, reason, policy combination and shared-value path: once-only build, cached throwable, re-entrance, timing excluded.
- End-to-end fixtures under `testing/fixtures/`, run under both tools: external rule skipped locally; rule fails in CI (`archgate.ci=true`); `serviceUnavailable=skip`; offline; optional variable present and absent; shared value used by two rules (one `Shared:` line); grouped violations. The fixture model probably needs a way to pass system properties and environment variables to both runners; that is left to the builder.

### Docs

`docs/configuration.md` gets sections "Rules that call external services", "Sharing a load between rules", "Checking only the files a pull request changed" and an updated "Reading the report". `docs/extending.md` and the README get a short example rule. `AGENTS.md` contracts are updated:

- Contract 2 adds the case of an external rule that cannot run in CI.
- Contract 3 adds `checkViolationGroups`.
- A new contract: skipped is a third outcome, produced only by `ExternalDependencyUnavailable`.
- Contract 9 lists the new pinned report strings.

The `changedFiles` section stays generic: CI computes the changed `.java` files of a pull request, writes them one per line to a UTF-8 file, and passes `-Darchgate.changedFiles=<file>`. It leaves the variable unset when build files changed or the diff cannot be computed. A rule that supports it checks only those files when the variable is set, makes no request when none of its files changed, and fails when the file cannot be read.

## Delivery

Three pull requests. Each is a Conventional Commit `feat:` or `docs:`, so Release Please cuts minor releases.

1. `feat: let rules read external variables and skip outside CI`: `Environment` including `optional`, `ExternalDependencyUnavailable`, `ExternalPolicy`, the skip outcome, Gradle caching, fixtures, docs.
2. `feat: share a load between rules and group violations by reason`: run services `shared`, `Shared:` line, `ViolationGroup`, report blocks joined with the platform line separator, fixtures, docs.
3. `docs: describe the archgate.changedFiles convention`.

Feature 3 of the original list (the platform line separator) only exists together with report blocks, so it ships inside PR 2.

### Reference output

Detail lines are indented by four spaces, and violation lines by eight (`    - ` inside the message plus the block's four).

```
[PASS]  MAIN+TEST     41 ms  No Thread.sleep
[SKIP]  TEST           0 ms  tracker-check
    Missing TRACKER_USER, TRACKER_PASSWORD. Pass them as -DNAME=value or as environment variables.
    Skipped because this is not a CI run (no CI variable set); in CI this fails the build.
[INFO]  TEST         800 ms  Tracker case kept
    Every case id on a test method must identify an existing case.
    Violations (1) "case does not exist":
        - C101      FirstTest.java:12
    Violations (2) "case is deleted":
        - C102      SecondTest.java:26
        - C103      ThirdTest.java:51
Class import: all 530 ms, main 380 ms, test 210 ms
Shared: issue index 2150 ms
============================================================
Gate PASSED: 3 rules, 1 info, 1 skipped
```

In CI, with `archgate.ci.serviceUnavailable=skip`, the second detail line of a skip reads `Skipped in CI because the service is unavailable (archgate.ci.serviceUnavailable=skip); the rule checked nothing.` A rule that cannot run in CI fails the build with `Rule '<name>' cannot run in CI (CI is set): <message>`. If `archgate.offline=true` is ignored in CI, the build logs `archgate.offline=true is ignored in CI (CI is set); external rules run.`

Where this sample differs from the existing `ReportFormatter` (the column widths, or the wording of the verdict line), the existing quokkify format wins and only the new lines are added.

## Left to the builder

- Exact names of the run-services type and the outcome state, provided they stay project-neutral.
- How fixtures pass properties and environment to Maven and Gradle runs.
- `RunServices` arrives in PR 1 with `environment()` only and gains `shared(...)` in PR 2.
- Where in the Gradle output file `readEnvironment` is recorded, as long as the report text itself is unchanged.
