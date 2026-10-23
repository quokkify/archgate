# Structure Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move repeated build configuration and duplicated fixture runners into their shared homes and align `AGENTS.md` with the code, without changing anything a consumer sees.

**Architecture:** Build repetition moves into `build-logic` (a new `archgate.rules-conventions` plugin and a `FixtureTests` Groovy helper next to `ArchgateVersions.groovy`). Maven/Gradle fixture runners move into `testing/fixtures-support` (`MavenRun`, `GradleRun`). Docs and hygiene follow.

**Tech Stack:** Java 21, Gradle 9.8.2 (wrapper), Groovy precompiled script plugins, JUnit 5, AssertJ, maven-verifier, Gradle TestKit.

**Spec:** `docs/specs/2026-10-23-structure-cleanup-design.md`

## Global Constraints

- Branch `chore/structure-cleanup`; Conventional Commits ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`; never push.
- Read `AGENTS.md` before editing; its 14 contracts bind every task (report text is an output contract; `api`/`core` stay light; no services file in `rules-*`; public-repo hygiene).
- No public API change, no plugin behaviour change, no report-text change; existing tests keep their assertions.
- Every task ends with `./gradlew check` green (includes `mavenIT`, `functionalTest`, `testing:parity:test`, jacoco ≥ 80 % on api/core/context-*/rules-*). Compare test counts with the baseline in `.superpowers/sdd/<plan>/baseline.txt` (controller provides the path).
- Style: palantir-java-format via Spotless (`./gradlew spotlessApply` before committing Java), Checkstyle `config/checkstyle/checkstyle.xml`, `Objects.isNull`/`Objects.nonNull` instead of `== null`.
- Stage paths explicitly; never `git add -A`, `git stash`, `git clean`.

## Review Focus

- `mavenIT` promotes the Maven cache once after all runs of a scenario; parity promotes after every run → `MavenRun` must not promote; callers keep their own `cache.promote(localRepo)` (Task 2).
- A failing Maven build must still be reported as `succeeded = false`, not as an exception → `MavenRun.execute` catches `VerificationException` from `execute()` only (Task 2).
- `CachingFunctionalTest` and `ArchgateElementsFunctionalTest` import `GradleFixtureFunctionalTest.runner/materialize/TASK` statically → those members keep working (Task 2).
- Moving `archfixtures/**` exclusion to all modules must not hide checkstyle on real test code → exclude pattern stays exactly `archfixtures/**` (Task 1).
- `FixtureTests` must keep the task cacheable/up-to-date: same input property names (`itRepoArtifacts`, `fixtures`) and path sensitivities as today (Task 1).

---

### Task 1: Build conventions for rules modules and fixture tests

**Files:**
- Create: `build-logic/src/main/groovy/archgate.rules-conventions.gradle`
- Create: `build-logic/src/main/groovy/FixtureTests.groovy`
- Modify: `build-logic/src/main/groovy/archgate.java-conventions.gradle` (checkstyle block)
- Modify: `rules-archunit/build.gradle`, `rules-javaparser/build.gradle`, `rules-classgraph/build.gradle`, `context-classgraph/build.gradle`
- Modify: `maven-plugin/build.gradle:76-93` (`mavenIT`), `gradle-plugin/build.gradle:41-56`, `testing/parity/build.gradle:22-36`

**Interfaces:**
- Produces: plugin id `archgate.rules-conventions`.
- Produces: `FixtureTests.configure(Test task, Project project, boolean withMaven)` — sets on `task`: `dependsOn project.rootProject.tasks.named('publishToItRepo')`; `inputs.files(rootProject.itRepoArtifacts).withPropertyName('itRepoArtifacts').withPathSensitivity(NAME_ONLY)`; `inputs.dir(rootProject.file('fixtures')).withPropertyName('fixtures')`; system properties `archgate.itRepo` (root `build/it-repo` path), `archgate.fixtures` (root `fixtures` path), `archgate.version` (`project.version`); `testLogging { events 'failed'; exceptionFormat = 'full' }`. When `withMaven`: `dependsOn ':maven-plugin:unpackMaven'`, `maven.home` = `:maven-plugin` build dir `maven/apache-maven-<mavenRuntime version from the libs catalog>`, `archgate.mavenCache` = `rootProject.mavenItCache` path.

- [ ] **Step 1: `archgate.rules-conventions`** — body is today's block from `rules-archunit/build.gradle:18-21` verbatim (`tasks.named('test', Test) { dependsOn jar; systemProperty 'archgate.rulesJar', … }`). Apply it in the three `rules-*` modules (`plugins { id 'archgate.rules-conventions' }`) and delete their copies.

- [ ] **Step 2: Checkstyle** — in `archgate.java-conventions.gradle` add `tasks.named('checkstyleTest') { exclude 'archfixtures/**' }`; delete the blocks in `context-classgraph`, `rules-archunit`, `rules-classgraph`.

- [ ] **Step 3: `FixtureTests`** per Interfaces; replace the shared lines in `mavenIT` (`withMaven: true`), `functionalTest` (`false`) and parity `test` (`true`). Each task keeps its own `group`, `description`, `testClassesDirs`, `classpath`. Parity keeps `evaluationDependsOn` and its dependencies.

- [ ] **Step 4: Verify**

Run: `./gradlew check --console=plain` and `git grep -nE "archgate\.(rulesJar|itRepo|fixtures|version|mavenCache)|archfixtures" -- '*/build.gradle'`
Expected: BUILD SUCCESSFUL with the baseline test counts; grep prints only `testing/fixtures-support/build.gradle` (its `archgate.fixtures`/`archgate.root` for `DocsLinkTest`, out of scope).
Then: `./gradlew :maven-plugin:mavenIT --console=plain` twice in a row → second run `UP-TO-DATE` (inputs stayed cacheable).

- [ ] **Step 5: Commit** — `build: add rules conventions and fixture test helper`

### Task 2: Shared Maven and Gradle fixture runners

**Files:**
- Create: `testing/fixtures-support/src/main/java/dev/quokkify/archgate/testing/MavenRun.java`
- Create: `testing/fixtures-support/src/main/java/dev/quokkify/archgate/testing/GradleRun.java`
- Modify: `testing/fixtures-support/build.gradle` (`implementation gradleTestKit()`)
- Modify: `maven-plugin/src/mavenIT/java/dev/quokkify/archgate/maven/it/MavenFixtureIT.java:55-73`
- Modify: `gradle-plugin/src/functionalTest/java/dev/quokkify/archgate/gradle/GradleFixtureFunctionalTest.java:39-46`
- Modify: `testing/parity/src/test/java/dev/quokkify/archgate/parity/ToolRunners.java:51-89`

**Interfaces:**
- Produces: `public final class MavenRun` with `public record Outcome(boolean succeeded, String log)` and `public static Outcome execute(Path project, Path localRepo, MavenCache cache, Expectation expectation) throws VerificationException, IOException` — creates `ForkedMaven.pinJavaHome(new Verifier(project.toString()))`, sets local repo and log file `log-run-<run>.txt`, adds `-s settings.xml -Pit-repo <cache.tailArgument()> verify` plus `expectation.args(BuildTool.MAVEN)`, runs it, maps a `VerificationException` from `execute()` to `succeeded = false`, reads the log file. Does **not** promote the cache.
- Produces: `public final class GradleRun` with `public static GradleRunner runner(Path project, List<String> args)` — `check --stacktrace` plus `args`, `withProjectDir`, `withPluginClasspath()`.
- Consumes: `ForkedMaven`, `MavenCache`, `Expectation`, `BuildTool` (existing in `fixtures-support`).

- [ ] **Step 1: Implement `MavenRun` and `GradleRun`** per Interfaces (code moved from `MavenFixtureIT.run` and `GradleFixtureFunctionalTest.runner`, not rewritten).

- [ ] **Step 2: Delegate**
  - `MavenFixtureIT.run` calls `MavenRun.execute`, keeps its assertions and its single `cache.promote` in `runsScenario`.
  - `GradleFixtureFunctionalTest.runner` becomes a one-line delegate to `GradleRun.runner` (keeps the static members other functional tests import).
  - `ToolRunners.runMaven` calls `MavenRun.execute` inside its existing `try`, keeps `cache.promote(localRepo)` in a `finally` around the call, and wraps `VerificationException | IOException` as today. `runGradle` builds its runner with `GradleRun.runner`.

- [ ] **Step 3: Verify**

Run: `./gradlew spotlessApply check --console=plain` and `git grep -n "new Verifier\|GradleRunner.create" -- maven-plugin gradle-plugin testing/parity`
Expected: BUILD SUCCESSFUL, baseline test counts; grep prints only `gradle-plugin` functional tests other than `GradleFixtureFunctionalTest` if they build their own runner (list them in the report; do not change them).

- [ ] **Step 4: Commit** — `test: share Maven and Gradle fixture runners`

### Task 3: AGENTS.md, docs and hygiene

**Files:**
- Modify: `AGENTS.md`, `.copier-answers.core.yml:5`, `docs/specs/2026-10-21-archgate-gradle-design.md:4`
- Modify: `core/src/test/java/dev/quokkify/archgate/context/archunit/ArchUnitContext.java`
- Modify: `gradle-plugin/src/main/java/dev/quokkify/archgate/gradle/ArchgateElements.java:156`; `testing/fixtures-support/src/main/java/dev/quokkify/archgate/testing/{MavenCache,GradleProjectWriter,MavenProjectWriter,PropertyFiles,Fixture}.java` (null comparisons)
- Delete: `docs/plans/2026-10-21-archgate-gradle.md`

- [ ] **Step 1: `AGENTS.md`** — apply every bullet of spec §5 "AGENTS.md": module-map additions (`SimpleProjectLayout`, `ModuleDirs`, `ReportSink`, `ArchUnitContexts`, `DirectoryArchUnitContext`, `DirectoryJavaParserContext`, `DirectoryClassGraphContext`, `ExistingDirectories`, `FailureMessage`, `GateWorkParameters`, `BomCompletenessTest`, `archgate.rules-conventions`, `FixtureTests`, `MavenRun`, `GradleRun`), the `.support` package, test-scope `core` dependency note, English-only carve-out for `docs/ci-kit.md` and `docs/specs/`, mention of `.ci/`, `.copier-answers*.yml`, `docs/specs/`. Verify every class name with `git ls-files` before writing it.

- [ ] **Step 2: Hygiene**
  - `.copier-answers.core.yml`: `project_name: archgate`.
  - `git rm docs/plans/2026-10-21-archgate-gradle.md`; status line of `docs/specs/2026-10-21-archgate-gradle-design.md` → `Status: approved design; implemented in 0.1.0`.
  - Core fake: javadoc `/** Shares the binary name of the real ArchUnit context so tests assert the exact message a user sees. */`.
  - Null comparisons → `Objects.isNull` / `Objects.nonNull` (behaviour identical; ternaries keep their branches).

- [ ] **Step 3: Verify**

Run: `./gradlew spotlessApply check --console=plain` and `git grep -nE "[!=]= null" -- gradle-plugin/src/main testing/fixtures-support/src/main`
Expected: BUILD SUCCESSFUL (incl. `DocsLinkTest`), baseline test counts; grep prints nothing.

- [ ] **Step 4: Commits** — `style: use Objects.isNull in gradle-plugin and fixtures-support` (null comparisons only), then `docs: align AGENTS.md with the code and tidy repository files` (everything else).

### Final

- [ ] Whole-branch review by a separate reviewer against the spec's success criteria; `docs/plans/2026-10-23-structure-cleanup.md` is deleted in the last commit after the review, like the 0.1.0 plan.
