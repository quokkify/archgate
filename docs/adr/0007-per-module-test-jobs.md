# 0007. CI runs tests as one job per module

Status: accepted

## Context

Until issue #36 the `java` CI job ran `./gradlew build` on one runner:

- It took 3.5 to 5 minutes, most of it `:testing:test`.
- Gradle stopped at the first failed test task, so the Allure report missed every module after it.
- The report read one results directory, so a root `collectAllureResults` task copied every module's `build/allure-results` into the root `build/allure-results` for CI to upload.

Two wirings could split the run by module:

- (a) As in quokkify/q4j: a project-owned test workflow with a module matrix, and the ci-kit `allure` module in `external` mode.
- (b) ci-kit components: one `java` component per module, each repeating setup, lint and build.

ci-kit 4.2.0 added a third option: `test_modules` on a single `java` component.

## Decision

The `archgate-java` component lists every Gradle project that has tests under `test_modules`. ci-kit renders:

- one `checks` job, which runs setup, lint and `build -x test`;
- one `test (<module>)` job per module, in parallel with `fail-fast: false`.

Each module job runs `./gradlew :<module>:check`, so line-coverage verification still gates `api`, `core`, `context-*` and `rules-*`. It uploads that module's `build/allure-results` as `allure-results-archgate-java-<module>`. The `allure` module stays in `component` mode, and the root `collectAllureResults` task is removed.

`build -x test` skips `jacocoTestCoverageVerification` in `checks`, because there is no execution data.

ci-kit exports `MODULE_PATH` to each module job. q4j's `SuiteListener` would then replace the `subSuite` label (the test class) with the module. The test task removes `MODULE_PATH` from the test JVM's environment. The module is already the `parentSuite` label, through `SUITE_NAME`.

## Consequences

- No project-owned test workflow. The module jobs, the `Validate` gate and the Allure report all come from ci-kit, and update with `copier update`.
- A new Gradle project with tests must be added to `test_modules` in `.copier-answers.java.yml`, or CI does not run its tests.
- `:testing` depends on `publishToItRepo`, so its job builds every module. It still sets the wall-clock time.
- Lint runs again in every module job, because `check` includes Checkstyle and Spotless. This is cheap next to the tests, and each job stays a full check of its module.
