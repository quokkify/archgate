# External Rules, Shared Values and Grouped Violations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rules can read external variables (skipping outside CI, failing closed in CI), share one expensive load per gate run, and report violations grouped by reason, with identical reports under Maven and Gradle.

**Architecture:** `api` gains `Environment`, `ExternalDependencyUnavailable`, `RunServices`, `ViolationGroup`. `AnalysisContext` exposes `environment()` and `shared()`, and every built-in context delegates them to `ProjectLayout.services()`. `core` owns the variable lookup, the CI policy, the shared-value store, the skip outcome and the report. The plugins only build the variable lookup and log report blocks. Delivery is three PRs, each a stacked branch.

**Tech Stack:** Java 21, Gradle (`./gradlew`), TestNG + AssertJ, Gradle TestKit, forked Maven runs in `testing/`.

**Spec:** `docs/superpowers/specs/2026-11-20-external-rules-and-shared-values-design.md`. Read it first. Its "Reference output" section is the exact text to produce.

## Global Constraints

- `api` gets no new dependency. `core` stays free of analysis libraries and build-tool types. No logging framework outside the plugins.
- Gradle: variables, CI detection and switches are read at **execution time inside the worker**, never at configuration time. The configuration cache must stay reusable (`CachingFunctionalTest`).
- `archgate.ci`, `archgate.offline` and `archgate.ci.serviceUnavailable` are properties only. They get no `CheckMojo` `@Parameter` and no `ArchgateExtension` property.
- CI is detected from `CI` only. The source text is `CI is set` or `archgate.ci=<value>`. Without either it is `no CI variable set`.
- Lookup order, first non-blank value wins. Maven: session user properties, then `System.getProperty`, then `System.getenv`. Gradle worker: `System.getProperty`, then `System.getenv`.
- Report text is a contract. Every new line is pinned in `testing/fixtures/*/expected.properties` in the same commit, and Maven and Gradle must print the same text.
- Public repository: no employer, host, ticket or product names anywhere, including fixture names and variable names. Use `TRACKER_TOKEN`-style names.
- Every PR runs `./gradlew build` green before push. Commits use Conventional Commits and end with the `Co-Authored-By` attribution line.

## Review Focus

1. **`CI=true` in the test JVM's environment** (GitHub Actions sets it). Fixtures with external rules must not flip to CI mode. Every external-rule fixture run passes `archgate.ci` explicitly, and Task 7 adds a guard test.
2. **The same `ExternalDependencyUnavailable` instance thrown to several rules** (a cached `shared` failure) must give one skip row per rule. It must not trigger `keepFirst` suppression or a self-suppression `IllegalArgumentException`. Task 10 adds a test for this.
3. **A rule that calls `require` and then throws an unrelated exception** is still unable-to-verify, never a skip. Task 4 adds a test.
4. **Windows line endings.** Report blocks joined with `System.lineSeparator()` must not make the report file or the Maven/Gradle comparison differ. Task 11 pins this with a test that formats with `\r\n`.
5. **Third-party `AnalysisContext` or `ProjectLayout`** that does not implement the new methods must fail with an actionable `UnableToVerifyError`, not `AbstractMethodError` or NPE. Task 2 adds the test.

---

## PR 1 — `feat: let rules read external variables and skip outside CI`

Branch `feat/external-rules`, created from `docs/external-rules-design` so the spec and this plan ship with PR 1.

### Task 1: Gradle build-cache spike (throwaway)

**Files:** a scratch Gradle build under the session scratchpad. It is never committed.

- [ ] **Step 1:** Build a minimal `@CacheableTask` with one input and one output. Its action writes a marker file whose content is chosen at execution time. Register `outputs.doNotCacheIf("reads environment") { marker says yes }`.
- [ ] **Step 2:** Run with `--build-cache` on Gradle 9.7.1 (`gradle wrapper --gradle-version 9.7.1`) and on the repository's current wrapper version. Clean, rerun, and record whether the run came `FROM-CACHE`.
- [ ] **Step 3:** Record the verdict in one line: **VETO WORKS** (the second run executes) or **VETO TOO EARLY** (the second run is `FROM-CACHE`). Task 6 takes the matching branch. Delete the scratch build.

### Task 2: API — `Environment`, `ExternalDependencyUnavailable`, `RunServices`

**Files:**
- Create: `engine/api/src/main/java/dev/quokkify/archgate/api/Environment.java`, `ExternalDependencyUnavailable.java`, `RunServices.java`
- Modify: `engine/api/.../AnalysisContext.java`, `ProjectLayout.java`
- Test: `engine/api/src/test/java/dev/quokkify/archgate/api/EnvironmentTest.java`, `ExternalDependencyUnavailableTest.java`, `RunServicesFallbackTest.java`

**Interfaces — Produces:**
- `interface Environment { Map<String,String> require(String... names); default Optional<String> optional(String name); default ExternalDependencyUnavailable unavailable(String service, Throwable cause); }`. The `optional` default throws `UnableToVerifyError("This Environment provides no optional variables, so rules that read them cannot run with it.")`. The `unavailable` default returns `ExternalDependencyUnavailable.serviceUnavailable(service, cause)`.
- `final class ExternalDependencyUnavailable extends Error` with:
  - `enum Reason {MISSING_VARIABLES, OFFLINE, SERVICE_UNAVAILABLE}`
  - factories `missingVariables(List<String>)`, `offline()`, `serviceUnavailable(String, Throwable)`
  - accessors `reason()`, `missingVariables()`, `Optional<String> service()`
  - the three messages exactly as the spec gives them
- `interface RunServices { Environment environment(); }`. PR 2 adds `shared`.
- `ProjectLayout.services()`: `default RunServices services()` throws `UnableToVerifyError("<layout class> provides no run services, so rules that read the environment or share values cannot run with it. Update it to implement ProjectLayout.services().")`.
- `AnalysisContext.environment()`: `default Environment environment()` throws `UnableToVerifyError("<context class> provides no environment. Update it to delegate environment() to ProjectLayout.services().")`.

- [ ] **Step 1: Write failing tests.**
  - `missingVariablesMessageListsAllNames`: `missingVariables(List.of("A","B")).getMessage()` equals `Missing A, B. Pass them as -DNAME=value or as environment variables.`
  - `missingVariablesRejectsEmpty`: throws `IllegalArgumentException`.
  - `offlineMessage`: equals `External rules are switched off by archgate.offline=true.`
  - `serviceUnavailableUsesCauseMessageOrClass`: with `new IOException("Connect timed out")` the message is `Tracker is unavailable: Connect timed out`. With `new IOException()` it is `Tracker is unavailable: java.io.IOException`. The cause is kept.
  - `defaultOptionalIsUnableToVerify`.
  - `contextWithoutServicesFailsActionably`: an anonymous `AnalysisContext` calling `environment()` throws `UnableToVerifyError` whose message contains `ProjectLayout.services()`. This covers Review Focus 5.
- [ ] **Step 2:** Run `./gradlew :api:test`. Expected: compilation FAIL.
- [ ] **Step 3:** Implement the types above. Add Javadoc on every public member: `api` is published and the build fails on Javadoc warnings.
- [ ] **Step 4:** Run `./gradlew :api:test`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(api): add Environment and ExternalDependencyUnavailable`.

### Task 3: Core — `VariableEnvironment` and `ExternalPolicy`

**Files:**
- Create: `engine/core/src/main/java/dev/quokkify/archgate/core/VariableEnvironment.java`, `ExternalPolicy.java`
- Test: `engine/core/src/test/java/dev/quokkify/archgate/core/VariableEnvironmentTest.java`, `ExternalPolicyTest.java`

**Interfaces — Produces:**
- `final class VariableEnvironment implements Environment`:
  - `VariableEnvironment(Function<String,String> lookup, boolean offline)`
  - `boolean wasRead()`: true once `require` or `optional` was called, even when the call then threw
  - `require` validates names (null or blank → `IllegalArgumentException("Variable names must not be null or blank")`), throws `offline()` if offline, then `missingVariables(...)` with every missing name in the requested order. Blank values count as missing.
  - `optional` ignores `offline`.
- `record ExternalPolicy(boolean ci, String ciSource, boolean offline, boolean offlineIgnored, ServiceUnavailable serviceUnavailable)` with:
  - `enum ServiceUnavailable {FAIL, SKIP}`
  - constants `CI_PROPERTY="archgate.ci"`, `OFFLINE_PROPERTY="archgate.offline"`, `SERVICE_UNAVAILABLE_PROPERTY="archgate.ci.serviceUnavailable"`
  - `static ExternalPolicy detect(Function<String,String> lookup)`
  - `boolean skipsInCi(ExternalDependencyUnavailable)`
  - `Optional<String> offlineWarning()`, which returns `archgate.offline=true is ignored in CI (<ciSource>); external rules run.` when `offlineIgnored`

- [ ] **Step 1: Write failing tests.**
  - `requireReturnsValuesInOrder`
  - `requireListsEveryMissingAndBlankName`: lookup `{A=" ", B=null}` gives a message starting with `Missing A, B.`
  - `offlineSkipsBeforeLookup`
  - `optionalIgnoresOffline`
  - `optionalBlankIsEmpty`
  - `wasReadAfterRequireEvenWhenItThrows`
  - `ciFromCiVariable`: `CI=true` gives `ci` true and source `CI is set`. `CI=false` and a blank `CI` give not-CI with source `no CI variable set`.
  - `ciOverride`: `archgate.ci=FALSE ` with `CI=true` gives not-CI, source `archgate.ci=FALSE`.
  - `invalidCiValueFails`: `archgate.ci=yes` throws `UnableToVerifyError` with message `Unknown archgate.ci value 'yes'. Use true or false.`
  - `offlineIgnoredInCi`: the warning text is exact.
  - `serviceUnavailableParsing`: blank gives FAIL, ` Skip ` gives SKIP, `maybe` gives `Unknown archgate.ci.serviceUnavailable value 'maybe'. Use fail or skip.`
  - `skipsInCiOnlyForServiceUnavailableWithSkip`: a table over the three reasons × ci × FAIL/SKIP.
- [ ] **Step 2:** Run `./gradlew :core:test --tests '*VariableEnvironmentTest' --tests '*ExternalPolicyTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same command. Expected: PASS.
- [ ] **Step 5:** Commit `feat(core): detect CI and read rule variables`.

### Task 4: Core — skip outcome in `GateRunner`, `RuleOutcome` and `ReportFormatter`

**Files:**
- Modify: `engine/core/.../GateRunner.java`, `RuleOutcome.java`, `GateResult.java`, `ReportFormatter.java`
- Test: `GateRunnerTest.java`, `ReportFormatterTest.java`, `TestRules.java`

**Interfaces:**
- Consumes: `ExternalPolicy` (Task 3) and `ExternalDependencyUnavailable` (Task 2).
- Produces:
  - `record ExternalSkip(String message, String reason)` in `core`. `reason` is the second detail line.
  - `RuleOutcome` gains a trailing component `ExternalSkip externalSkip`, which is nullable, plus `boolean externallySkipped()`. The existing constructors pass `null`.
  - `GateResult.externallySkipped()` returns the count.
  - `GateRunner.run(List<ArchitectureRule<?>>, ContextResolver, ExternalPolicy)` replaces the two-argument method. Update every caller, `TestRules` and tests included.

- [ ] **Step 1: Write failing tests.**
  - `GateRunnerTest.missingVariableSkipsOutsideCi`: the outcome is `externallySkipped`, `finding` is null, and `reason` equals `Skipped because this is not a CI run (no CI variable set); in CI this fails the build.`
  - `missingVariableFailsInCi`: throws `UnableToVerifyError` with the message `Rule 'ext' cannot run in CI (CI is set): Missing T. Pass them as -DNAME=value or as environment variables.`
  - `serviceUnavailableSkipsInCiWhenAllowed`: `reason` equals `Skipped in CI because the service is unavailable (archgate.ci.serviceUnavailable=skip); the rule checked nothing.`
  - `requireThenUnrelatedFailureIsUnableToVerify`: covers Review Focus 3.
  - `skipNeverFailsGate`: `failsOn(Optional.of(INFO))` is false when the only outcome is a skip.
  - `ReportFormatterTest.skipRowsBetweenPassAndFindings`: the order is PASS, SKIP, finding. The skip row label is `[SKIP]`, followed by two lines indented by four spaces.
  - `verdictCountsSkips`: `Gate PASSED: 2 rules, no findings, 1 skipped`.
- [ ] **Step 2:** Run `./gradlew :core:test`. Expected: FAIL.
- [ ] **Step 3:** In `GateRunner.evaluate`, catch `ExternalDependencyUnavailable` around `rule.verify` **before** the generic `Throwable`. Then:
  - outside CI → `ExternalSkip`
  - in CI with `policy.skipsInCi(signal)` → `ExternalSkip`
  - otherwise → `UnableToVerifyError` with the message above and the signal as cause
- [ ] **Step 4:** In `ReportFormatter`, append `, N skipped` to `counts` when N > 0, and print skip rows with `LINE_FORMAT` and the label `[SKIP]`.
- [ ] **Step 5:** Run `./gradlew :core:test`. Expected: PASS.
- [ ] **Step 6:** Commit `feat(core): report rules that cannot reach their service as skipped`.

### Task 5: Core and contexts — wire run services through the launcher

**Files:**
- Modify:
  - `engine/core/.../SimpleProjectLayout.java`, which gets a trailing `RunServices services` component
  - `launch/LaunchRequest.java`, which gets `Function<String,String> lookup` and `Optional<Path> stateFile`
  - `launch/GateLauncher.java`
  - `DirectoryArchUnitContext`, `DirectoryJavaParserContext` and `DirectoryClassGraphContext`, which override `environment()` with `layout.services().environment()`
  - test helpers that build a `SimpleProjectLayout` or `LaunchRequest`: `TestRules`, `FixtureContexts`, `ContextResolverTest`, `LaunchContextProvider`, the context tests, and `GateWorkAction`/`CheckMojo`, which pass the lookup and an empty state file until Task 6
- Create: `engine/core/.../GateRunServices.java`, a `RunServices` implementation that holds a `VariableEnvironment`
- Test: `engine/core/src/test/java/dev/quokkify/archgate/core/launch/GateLauncherTest.java` (extend it if it exists), plus one test per context module: `environmentDelegatesToLayout`

**Interfaces — Produces:**
- `GateLauncher.run(LaunchRequest, ReportSink)` keeps its signature. Internally it calls `ExternalPolicy.detect(request.lookup())` before loading rules, so an invalid switch fails even with zero external rules. It logs `offlineWarning()` at `WARNING` through the sink before the report, and logs the report at least at `WARNING` when `externallySkipped() > 0`.
- The state file: when `stateFile` is present, `GateLauncher` writes `readEnvironment=true|false` to it, UTF-8, after the run, whether the run passed or failed. It writes nothing when the run throws `UnableToVerifyError`.

- [ ] **Step 1: Write failing tests.**
  - `invalidSwitchFailsWithoutExternalRules`
  - `offlineWarningLoggedInCi`
  - `skipRaisesReportLevelToWarning`
  - `stateFileRecordsEnvironmentRead`: one rule calls `optional("x")` → `readEnvironment=true`. With no such rule → `false`.
  - per context: `environmentDelegatesToLayout`
- [ ] **Step 2:** Run `./gradlew :core:test :context-archunit:test :context-javaparser:test :context-classgraph:test`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same command, then `./gradlew build -x :testing:test`. Expected: PASS.
- [ ] **Step 5:** Commit `feat: pass run services to every analysis context`.

### Task 6: Plugins — variable lookup and Gradle up-to-date and caching

**Files:**
- Modify: `integration/maven-plugin/.../CheckMojo.java`, `integration/gradle-plugin/.../GateWorkAction.java`, `GateWorkParameters.java`, `ArchgateCheckTask.java`, `ArchgatePlugin.java` (only if outputs are configured there)
- Test: `testing/src/test/java/dev/quokkify/archgate/testing/e2e/CachingFunctionalTest.java`, Maven plugin unit test for the lookup order

**Interfaces:**
- Maven: `CheckMojo` builds `name -> first non-blank of session.getUserProperties(), System.getProperty, System.getenv`. The state file stays empty for Maven, which has no cache.
- Gradle: the worker builds `name -> first non-blank of System.getProperty, System.getenv`. A new `@OutputFile getStateFile()` sits next to the report: `build/archgate/state.properties` (same directory as the report file). It is passed to the worker. `outputs.upToDateWhen` returns false when the previous state file contains `readEnvironment=true`.
- Build cache:
  - **VETO WORKS** (Task 1): `outputs.doNotCacheIf("a rule read the environment", ...)` reads the state file.
  - **VETO TOO EARLY**: replace `@CacheableTask` with `@DisableCachingByDefault(because = "rules may read external services")`, and add `docs/adr/0006-archgate-check-not-build-cached.md`. Use the next free ADR number.

- [ ] **Step 1: Write failing tests in `CachingFunctionalTest`.**
  - `runThatReadsEnvironmentIsNotUpToDate`: a fixture with a rule calling `optional`. The second run outcome is `SUCCESS`, not `UP_TO_DATE`.
  - `runWithoutEnvironmentStaysUpToDate`: the existing `secondRunIsUpToDate` still passes.
  - **VETO WORKS:** `runThatReadsEnvironmentIsNotStoredInBuildCache`, and the existing `buildCacheHit` still passes.
  - **VETO TOO EARLY:** change `buildCacheHit` to assert the task is not `FROM_CACHE`, and justify the change in the commit body.
  - `configurationCacheIsReusedWithVariable`: `-DTRACKER_TOKEN=a`, then `-DTRACKER_TOKEN=b`. The configuration cache is reused, and the second run sees `b`.
- [ ] **Step 2:** Run `./gradlew :testing:test --tests '*CachingFunctionalTest'`. Expected: FAIL.
- [ ] **Step 3:** Implement. The Maven lookup gets a unit test, `userPropertyWinsOverSystemPropertyAndEnvironment`.
- [ ] **Step 4:** Run the same command and `./gradlew :maven-plugin:test`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(gradle): never reuse a result whose rules read the environment`.

### Task 7: End-to-end fixtures

**Files:**
- Modify: `testing/src/test/java/dev/quokkify/archgate/testing/Expectation.java`, which gets the key `run.N.systemProperties`, passed verbatim as `-Dk=v` to **both** tools (no `-D`→`-P` translation); `MavenRun.java` and `GradleRun.java`, which append them
- Create fixtures:
  - `testing/fixtures/external-rule/`: one rule needing `TRACKER_TOKEN`, one using `optional("archgate.changedFiles")` and reporting what it saw as an INFO finding
  - `testing/fixtures/external-service-unavailable/`: a rule that throws `context.environment().unavailable("Tracker", new IOException("Connect timed out"))`
- Test: `ExpectationTest` (or `FixtureSpecTest`) for the new key; a new guard `ExternalFixturesPinCiTest`, which fails if any fixture whose services file lists an external rule has a run without `archgate.ci` in `systemProperties`. This covers Review Focus 1.

**Runs in `external-rule/expected.properties`:**
1. `archgate.ci=false`, no token → success, contains `[SKIP]`, the missing-variable message, `Skipped because this is not a CI run (archgate.ci=false); in CI this fails the build.` and `, 1 skipped`.
2. `archgate.ci=false TRACKER_TOKEN=x` → success, `[PASS]` for the token rule.
3. `archgate.ci=false archgate.offline=true TRACKER_TOKEN=x` → `[SKIP]` with the offline message.
4. `archgate.ci=true` → `unableToVerify`, with the message `cannot run in CI (archgate.ci=true)`.
5. `archgate.ci=true archgate.offline=true TRACKER_TOKEN=x` → contains `archgate.offline=true is ignored in CI (archgate.ci=true); external rules run.`
6. `archgate.ci=false archgate.changedFiles=changed.txt` → contains the INFO finding that names `changed.txt`.
7. `archgate.ci=false archgate.ci.serviceUnavailable=maybe` → `unableToVerify` with the unknown-value message.

**Runs in `external-service-unavailable/`:**
- `archgate.ci=true` → `unableToVerify`.
- `archgate.ci=true archgate.ci.serviceUnavailable=skip` → success, `[SKIP]`, with the CI skip line.

- [ ] **Step 1:** Write the fixtures and expectations.
- [ ] **Step 2:** Run `./gradlew :testing:test`. Expected: FAIL until the key is supported.
- [ ] **Step 3:** Implement `systemProperties` support.
- [ ] **Step 4:** Run `./gradlew :testing:test`. Expected: PASS, with both tools printing identical normalized reports.
- [ ] **Step 5:** Commit `test: cover external rules end to end under Maven and Gradle`.

### Task 8: Docs and contracts, then open PR 1

**Files:** `docs/configuration.md`, `docs/extending.md`, `README.md`, `AGENTS.md`, `docs/faq.md` (only if it lists always-failing cases)

- [ ] **Step 1: Configuration doc.** Add the section "Rules that call external services" to `docs/configuration.md`. Cover:
  - the lookup order per tool
  - optional variables
  - CI detection
  - offline
  - service unavailable in CI
  - the situation table from the spec
  - "Reading the report" for `[SKIP]`
  - the Gradle caching behaviour decided in Task 6
- [ ] **Step 2: Extending doc.** Add a short example rule to `docs/extending.md` that uses `require` + `unavailable`, and a one-line pointer from the README.
- [ ] **Step 3: AGENTS.md.**
  - Contract 2 adds external rules in CI.
  - A new contract, "Skipped is a third outcome".
  - Contract 9 lists the new pinned strings.
  - The module map adds the new types.
- [ ] **Step 4:** Run `./gradlew build`. Expected: PASS. `DocsLinkTest` checks the anchors.
- [ ] **Step 5:** Commit `docs: describe rules that call external services`, push, and open a PR into `main` titled `feat: let rules read external variables and skip outside CI`. The body has a summary, the Task 1 verdict, a test plan and the release note.

---

## PR 2 — `feat: share a load between rules and group violations by reason`

Branch `feat/shared-values`, stacked on `feat/external-rules`. Retarget to `main` after PR 1 merges.

### Task 9: API — `ViolationGroup` and `checkViolationGroups`

**Files:**
- Create: `engine/api/.../ViolationGroup.java`
- Modify: `ArchitectureViolation.java`, `ArchitectureRule.java`
- Test: `ArchitectureViolationTest.java`, `ArchitectureRuleTest.java`

**Interfaces — Produces:**
- `record ViolationGroup(String title, List<String> violations)`: `title` must not be null, and `violations` is copied.
- `static ArchitectureViolation ArchitectureViolation.grouped(String expectedContract, List<ViolationGroup> groups)`.
- `default void ArchitectureRule.checkViolationGroups(String expectedContract, List<ViolationGroup> groups)`, which throws only if some group is non-empty.

- [ ] **Step 1: Write failing tests.**
  - `groupedMessageSkipsEmptyGroups`. With `System.lineSeparator()` as `NL`, the message equals `"Contract" + NL + "Violations (1) \"a\":" + NL + "    - x"`, given groups `a=[x]` and `b=[]`.
  - `checkViolationGroupsPassesWhenAllEmpty`.
  - `flatFormatUnchanged`: an existing test that must stay green.
- [ ] **Step 2:** Run `./gradlew :api:test`. Expected: FAIL.
- [ ] **Step 3:** Implement. Extract the shared `appendLines` helper.
- [ ] **Step 4:** Run `./gradlew :api:test`. Expected: PASS.
- [ ] **Step 5:** Commit `feat(api): group violations by reason`.

### Task 10: Shared values

**Files:**
- Modify: `RunServices.java`, which gets `<T> T shared(String name, Supplier<T> build)`; `AnalysisContext.java`, which gets a default `shared` throwing an actionable `UnableToVerifyError` like `environment()`; `GateRunServices.java`; `GateRunner.java`; `GateResult.java`, which gets a `Map<String, Duration> sharedBuildTimes` component; the three `Directory*Context` classes
- Test: `GateRunServicesTest.java`, `GateRunnerTest.java`

**Interfaces — Produces:** `GateRunServices.sharedBuildTimes()` returns `Map<String, Duration>`, sorted by name.

**Algorithm (not determined by the tests alone):**
- a `ConcurrentHashMap<String, FutureTask<Object>>`; the first caller runs the task and every caller awaits it
- `ExecutionException` is unwrapped: `RuntimeException` and `Error` are rethrown as-is (the same instance for every caller), and anything else is wrapped in `UnableToVerifyError`
- a `ThreadLocal<Set<String>>` of names being built detects re-entrance with `Shared value '<name>' is requested again while it is being built, so it can never be built.`
- a thread-local nanosecond counter accumulates all time spent inside `shared(...)`, and `GateRunner.evaluate` subtracts it from the rule's duration

- [ ] **Step 1: Write failing tests.**
  - `buildsOncePerName`: 8 virtual threads, an `AtomicInteger` counts 1.
  - `failureCachedAndRethrownSameInstance`.
  - `reentranceFails`.
  - `sharedTimeExcludedFromRuleDuration`: a build that sleeps 200 ms. The builder's and the waiter's durations are both < 100 ms, and `sharedBuildTimes` ≥ 200 ms.
  - `sharedUnavailableSkipsEveryUser`: two rules share a value whose build throws `unavailable(...)`. Both are `externallySkipped`, and `run` does not throw. This covers Review Focus 2.
- [ ] **Step 2:** Run `./gradlew :core:test`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :core:test` and the three context tests. Expected: PASS.
- [ ] **Step 5:** Commit `feat(core): share one load between rules in a gate run`.

### Task 11: Report blocks, `Shared:` line, grouped output

**Files:** `ReportFormatter.java`, `launch/GateLauncher.java`, `ReportFormatterTest.java`, `GateLauncherTest.java`

**Interfaces — Produces:**
- `ReportFormatter.format(...)` still returns `List<String>`, but each element is now a **block**. A rule row and its detail lines are joined with `System.lineSeparator()`. Header, footer, policy and timing lines are single-line blocks.
- `Shared: <name> <ms> ms, <name> <ms> ms` comes right after the `Model build:` line, and only when `sharedBuildTimes` is non-empty.
- `GateLauncher` sends one `sink.line` per block. The report file is written as blocks joined with `"\n"`, after replacing `System.lineSeparator()` inside blocks with `"\n"`, so the file is identical on every OS.

- [ ] **Step 1: Write failing tests.**
  - `findingRowAndDetailsAreOneBlock`.
  - `sharedLineAfterModelBuild`: `Shared: issue index 2150 ms`.
  - `noSharedLineWhenNothingShared`.
  - `groupedViolationsRender`: matches the spec's reference output for the grouped rule.
  - `reportFileUsesLfOnly`: covers Review Focus 4. Run the formatter with blocks built using `"\r\n"` through a package-private overload `format(GateResult, Optional<Severity>, String lineSeparator)` and assert the written file has no `\r`.
- [ ] **Step 2:** Run `./gradlew :core:test`. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `./gradlew :core:test`, then `./gradlew :testing:test`. Existing fixture expectations must still pass, since the normalizer compares per line. Adjust `ReportNormalizer` only if a Maven-prefix assertion breaks, and say why in the commit.
- [ ] **Step 5:** Commit `feat(core): log each rule with its details as one report block`.

### Task 12: Fixture, docs, and open PR 2

**Files:**
- `testing/fixtures/shared-value-grouped/`: two rules share `issue index` (one passes, one throws `checkViolationGroups` with two non-empty groups and one empty group)
- `docs/configuration.md`: the sections "Sharing a load between rules" and "Reading the report" (blocks, `Shared:`, groups). Explain that "once per run" means once per gate execution across all its modules.
- `README.md`, `docs/extending.md`, and contracts 3 and 9 in `AGENTS.md`

- [ ] **Step 1:** Write the fixture with expectations. Use `archgate.ci=false` in `systemProperties`. Assert:
  - `Shared: issue index` is printed exactly once (via `matches` with a count-anchored pattern, or `absent` of a second occurrence)
  - `Violations (2) "case is deleted":` is printed
  - the empty group's title is absent
- [ ] **Step 2:** Run `./gradlew :testing:test`. Expected: PASS for both tools.
- [ ] **Step 3:** Write the docs, then run `./gradlew build`. Expected: PASS.
- [ ] **Step 4:** Commit `docs: describe shared values and grouped violations`, push, and open a PR stacked on PR 1.

---

## PR 3 — `docs: describe the archgate.changedFiles convention`

Branch `docs/changed-files`, stacked on `feat/shared-values`.

### Task 13: changedFiles convention and spec cleanup

**Files:**
- Modify: `docs/configuration.md` (new section "Checking only the files a pull request changed") and `README.md` (one sentence plus a link)
- Delete: the spec and this plan under `docs/superpowers/`. Move the lasting decisions to an ADR: the services-on-context choice, plus the caching decision if Task 6 did not already write one.

- [ ] **Step 1:** Write the section from the spec's "Docs" paragraph:
  - a three-row table: unset or blank / names a file / names an unreadable file
  - who sets it: a generic CI recipe that computes the changed `.java` files of a pull request with `git diff --name-only <base>...HEAD`, writes them one per line, and passes `-Darchgate.changedFiles=<file>`, leaving it unset when build files change or the diff fails
  - the note that the engine never reads the file
- [ ] **Step 2:** Run `./gradlew :testing:test --tests '*DocsLinkTest'`. Expected: PASS.
- [ ] **Step 3:** Commit `docs: describe the archgate.changedFiles convention`, push, and open PR 3 stacked on PR 2.
