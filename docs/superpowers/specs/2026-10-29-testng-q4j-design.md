# TestNG and q4j for the test suite

Issue: #23. Status: approved design, pending implementation.

## Goal

Move the whole test suite from JUnit 5 to TestNG with the q4j `testng` extensions, add readable logs through SLF4J and Log4j 2, and run independent tests in parallel. The production code of every module stays unchanged and log-free.

## Decisions

| Topic | Decision |
|---|---|
| Runner | TestNG for every module, path (a) of the issue. JUnit 5 leaves the build. |
| q4j | `dev.quokkify:testng` (q4j `0.12.2`) is a `testImplementation` dependency of every Java project through `archgate.java-conventions`. This relaxes the issue's "`:testing` only" constraint; ADR 0006 records why. |
| Logging | SLF4J API in tests, Log4j 2 as the backend, both brought by `dev.quokkify:testng`. No main or `api` configuration gains a logging dependency (AGENTS.md contract 4). |
| Steps | Allure steps in the end-to-end test; build output attached as an Allure attachment and written to a per-run log file. No Allure report is wired into CI. |
| Retries | `RetryListener` is not registered: retries would hide flaky tests. |
| Parallelism | Scenarios run concurrently through a parallel `@DataProvider`; Maven and Gradle inside one scenario stay sequential. Other test methods run with `parallel=methods` in q4j's `Concurrency` block; `@SingleThread` methods run afterwards in the `Sequential` block. |

## Build changes

- `gradle/libs.versions.toml`: add `q4j = "0.12.2"` and `q4j-testng = { module = "dev.quokkify:testng", version.ref = "q4j" }`; drop `junit-bom`, `junit-jupiter`, `junit-platform-launcher` except where fixture compilation needs `junit-jupiter-api` (see below).
- `archgate.java-conventions`:
  - `testImplementation q4j-testng` and `assertj-core`;
  - `tasks.withType(Test) { useTestNG() }`;
  - `sourceSets.test.resources.srcDir rootProject.file('config/test-resources')`.
- `config/test-resources/` (shared by every module's tests):
  - `META-INF/services/org.testng.ITestNGListener` listing `dev.quokkify.listener.lifecycle.SuiteListener` and `dev.quokkify.listener.lifecycle.TestListener`;
  - `log4j2-test.xml`: console appender at `INFO`; a `Routing` appender keyed on the MDC values `scenario`, `tool` and `run` that writes `build/test-logs/<scenario>/<tool>-run<run>.log` when `scenario` is set;
  - `testng.properties` with `TEST_THREAD_COUNT=4`.
- `testing/build.gradle`: the `test` task sets `environment 'DATA_PROVIDER_THREAD_COUNT'` to the caller's value or `4`; `TEST_THREAD_COUNT` needs no entry, because q4j reads the environment variable itself; `testLogging` keeps `failed` events with full exceptions.

If Gradle's TestNG runner does not pick up `SuiteListener` through the services file (it is an `IAlterSuiteListener`), the convention registers it with `useTestNG { listeners << '…' }`. If neither works, Gradle's own `parallel`/`threadCount` options take over and `@SingleThread` methods move to a `single-thread` group run by a second `Test` task. This is checked first, before any test is migrated.

## Test migration

Mechanical mapping, applied to the 62 test classes outside `testing/fixtures`:

| JUnit 5 | TestNG |
|---|---|
| `org.junit.jupiter.api.Test` | `org.testng.annotations.Test` |
| `@ParameterizedTest` + `@ValueSource` / `@MethodSource` | `@Test(dataProvider = …)` + `@DataProvider` |
| `@BeforeEach` / `@AfterEach` | `@BeforeMethod` / `@AfterMethod` |
| `Assumptions.abort` / `assumeFalse` | `throw new SkipException(…)` |
| `Assertions` (JUnit) | AssertJ |
| `@TempDir Path temp` | a `TempDir` helper used with try-with-resources inside the method |

`TempDir` is a small `AutoCloseable` that creates a directory with `Files.createTempDirectory` and deletes it recursively on close. A method-local directory avoids shared fields, which TestNG would share between methods running in parallel on one instance. The helper lives in the test sources of each module that needs it; if q4j `files` already offers an equivalent, that is used instead.

`backends/archunit/rules/src/test/java/archfixtures/**` holds classes that are inputs to rules detecting JUnit annotations (for example `JupiterDisabledCase` with `@Disabled`). They are not tests and keep JUnit: that module declares `testCompileOnly` on `junit-jupiter-api` only, so the classes compile while no JUnit engine runs.

## End-to-end test

`FixtureEndToEndTest.runsScenario` keeps every assertion and message and gains structure:

- `scenarios()` becomes `@DataProvider(name = "scenarios", parallel = true)`.
- Each scenario gets its own temp directory (`TempDir`); the Maven local repository stays per scenario.
- Steps through `Allure.step`: `materialize <tool>`, `run <tool> #<run>`, `compare reports #<run>` (or `compare message #<run>` for `unableToVerify`).
- Around each Maven or Gradle run the test puts `scenario`, `tool` and `run` into the SLF4J MDC, logs the start and the outcome, and logs the full build output at `DEBUG` into the per-run file. The output is also attached to the step with `Allure.addAttachment`. q4j's `StepTestListener` is registered through `config/test-resources/META-INF/services/io.qameta.allure.listener.StepLifecycleListener` and `…TestLifecycleListener`, so steps are logged at `DEBUG` and attachments are kept only for failed tests. Allure writes to `build/allure-results` of each module through `allure.results.directory` in a shared `config/test-resources/allure.properties` (Gradle runs tests with the module directory as working directory).
- On failure the assertion message is unchanged, so the Gradle console report still shows it; the per-run log path is logged at `WARN`.

## Shared resources

| Resource | Safety |
|---|---|
| `MavenCache.promote` | Already copy-if-absent with an atomic move. A new test promotes the same tree from several threads and checks the result is complete. |
| `build/it-repo` | Read-only during tests. |
| Maven local repository | One per scenario temp directory. |
| Gradle TestKit directory | Shared by design; concurrent TestKit builds are supported. |
| Gradle build cache and configuration cache | `CachingFunctionalTest` depends on cache state, so its methods are `@SingleThread`. |

## Fail-closed and coverage guarantees

- `FixtureTools.fromSystemProperty`, `-PfixtureTools` and the `compared` logic are unchanged.
- Before the migration the test result XML of every module is saved. After it a script compares, per test class, the number of test cases, and for `FixtureEndToEndTest` the set of scenarios. Parameterised names differ between runners, so they are matched by their parameter value. Any missing case blocks the change.
- `jacocoTestCoverageVerification` keeps its 80 % line minimum.

## Measurement

`./gradlew clean build --no-build-cache` on `main` and on the branch, same machine. The numbers go into ADR 0006 and the PR.

## Documentation

- `docs/adr/0006-testng-q4j-test-suite.md`: context (issue #23 gaps), decision (a) over (b) and why, relaxed constraint, consequences, measured times.
- `AGENTS.md`: runner, logging location, `@SingleThread`, how to read `build/test-logs`.
- `docs/development.md`: how to run tests, thread count variables, where logs go.

## Out of scope

- Running Maven and Gradle concurrently inside one scenario.
- Publishing an Allure report from CI.
- Retries.
