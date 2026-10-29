# Development

## Requirements

- JDK 21. The build uses a Java 21 toolchain and compiles with `--release 21`.
- Nothing else: the Gradle wrapper (`./gradlew`) downloads Gradle, and the integration tests download a Maven 3.9.x distribution as a Gradle dependency.

## Build locally

```
./gradlew build
```

`build` runs `check`, which covers:

- unit tests of every module;
- `:testing:test`, the end-to-end test: every shared fixture runs once through a real Maven build (`maven-executor`) and once through a real Gradle build (TestKit), each checked against its `expected.properties`; for every fixture except `skip` the two reports must then be identical, and for a run marked `unableToVerify=true` neither tool may write a report and both must print the same message line. The same project holds the Gradle plugin's caching and `archgateElements` tests;
- Spotless (palantir-java-format) and Checkstyle (`config/checkstyle/checkstyle.xml`);
- JaCoCo coverage verification (80 % of lines) for `:api`, `:core`, `:context-*` and `:rules-*`;
- `DocsLinkTest`: relative links in the Markdown files resolve, the README rule table names existing rule classes, and the table lists every implemented rule;
- `denylistHookTest`: the pre-push hook blocks what it should.

To fix formatting: `./gradlew spotlessApply`.

### Useful tasks

| Task | What it does |
|---|---|
| `./gradlew build` | everything above |
| `./gradlew :testing:test` | the end-to-end test: every fixture under Maven and Gradle, results compared |
| `./gradlew :testing:test -PfixtureTools=gradle` | the fixtures under Gradle only (`maven` for Maven only); nothing is compared; the Gradle plugin's caching and `archgateElements` tests always run through Gradle TestKit |
| `./gradlew publishToItRepo` | publishes every module to `build/it-repo`, the local repository the integration tests resolve from |
| `./gradlew :testing:test --tests '*DocsLinkTest'` | only the documentation checks |

`:testing:test` depends on `publishToItRepo`, so it always tests freshly built artifacts. `publishToItRepo` first empties `build/it-repo`, and the tests take the published jars and POMs as inputs, so they are up to date while the code and the fixtures are unchanged. Each Maven run gets a fresh local repository for the ArchGate snapshots and reads third-party plugins and libraries from `build/maven-it-cache` (`-Dmaven.repo.local.tail`), which the runs fill as they go, so each artifact is downloaded once until `clean`.

### Running tests

The tests use TestNG with q4j and run in parallel by method. Run one module with `./gradlew :core:test`, one class with `--tests '*DocsLinkTest'`.

| Setting | Default | Effect |
|---|---|---|
| `TEST_THREAD_COUNT` | 4 | threads running test methods (`config/test-resources/testng.properties`) |
| `DATA_PROVIDER_THREAD_COUNT` | 4 | threads running the end-to-end scenarios (environment of `:testing:test`) |

An environment variable overrides the file default, for example `TEST_THREAD_COUNT=2 ./gradlew :core:test --rerun`. The override also applies when a Gradle daemon is already running.

Each end-to-end build run writes its output to `testing/build/test-logs/<scenario>/<tool>-run<run>.log`; a failing run names that file. In CI the same output is the `output` attachment of the run's step in the Allure report. The console prints every test result, prefixed with the suite name, and a summary line per test task. Each module's suite is named after the Gradle project (`core`, `rules-archunit`); set `SUITE_NAME` in the environment to override it. See [ADR 0006](adr/0006-testng-q4j-test-suite.md).

### Allure report

Every test task writes Allure results to its module's `build/allure-results` and clears them before it runs. `build-logic` is an included build whose tests do not run with `./gradlew test`; run them with `./gradlew -p build-logic test`. The report groups tests by module and class. Each end-to-end scenario is a case named after the scenario, tagged with the build tools it ran, with one step per build run that carries the Maven or Gradle output and a compare step that carries both `report.txt` files.

In CI the `java` component runs a `checks` job (lint and `build -x test`) and one `test (<module>)` job per Gradle project, in parallel, each running that project's `check`. A failing module does not stop the others. Each module job uploads its own `build/allure-results` as the `allure-results-archgate-java-<module>` artifact, on success and on failure. The module list is `test_modules` in `.copier-answers.java.yml`; add a project with tests there. The `Allure report` workflow then builds one report from every module's artifact, comments on the pull request with a summary and publishes it to GitHub Pages under `allure/pr-<number>`. The workflow is rendered by the ci-kit `allure` module; change its settings through `.copier-answers.allure.yml` (see [ci-kit](ci-kit.md)). See [ADR 0007](adr/0007-per-module-test-jobs.md).

To open the report locally, run the tests, then generate the report with the same Allure version and configuration as CI (Node.js required). Without a results directory, `generate` reads every module's `build/allure-results`:

```bash
./gradlew test
npx allure@3.22.1 generate --config .github/allure/allurerc.mjs --output build/allure-report
npx allure@3.22.1 open build/allure-report
```

### Shared fixtures

`testing/fixtures/<scenario>/` holds the sources and the services file of one scenario, plus `fixture.properties` and `expected.properties`. The test support in `:testing` generates a `build.gradle` or a `pom.xml` around it, so the same scenario runs under both tools. To add a scenario, add a directory there; the end-to-end test picks it up. Mark a run that must end with an "unable to verify" error with `run.N.unableToVerify=true`, so the end-to-end test compares its message instead of a report.

## Version

`version.txt` holds the last released version (`0.0.0` before the first release). A normal build appends `-SNAPSHOT`. A release build passes `-Prelease=true` and uses the version as is. Jars stamp `Archgate-Api-Version` in their manifest: the release version for release builds, `dev-SNAPSHOT` for every `-SNAPSHOT` build.

## Pre-push hook

The repository is public, so a local denylist of names that must never appear in it is checked before every push. The denylist file itself is local and is never committed.

1. Put one case-insensitive extended regular expression per line in `info/archgate-denylist` inside the common git directory:

   ```
   "$(git rev-parse --git-common-dir)/info/archgate-denylist"
   ```

2. Install the hook:

   ```
   ln -s ../../scripts/pre-push-denylist.sh "$(git rev-parse --git-common-dir)/hooks/pre-push"
   ```

   The common git directory is shared by linked worktrees, so one denylist and one hook serve all of them.

On every push the hook greps the pushed tree and the pushed commits (`git log -p`) with the denylist. A match, a missing denylist file or a tool error blocks the push. To check by hand:

```
git grep -niEf "$(git rev-parse --git-common-dir)/info/archgate-denylist"
```

It must print nothing.

## CI

Pull requests and pushes to `main` run the Gradle build with JDK 21. The workflows are generated from a shared template; see [Releasing](releasing.md) for the release and publishing workflows.
