# Structure cleanup after 0.1.0

Date: 2026-10-23
Status: approved design

## 1. Goal

Put repeated build configuration, duplicated test runners and misplaced test classes where the
repository's own rules (`AGENTS.md`) say they belong, and bring `AGENTS.md` back in line with the
code. Nothing a consumer sees changes: no public API, no plugin behaviour, no report text.

### Success criteria

1. `./gradlew check` is green, including `mavenIT`, `functionalTest` and `testing:parity`.
2. No module `build.gradle` sets `archgate.rulesJar`, `archgate.itRepo`, `archgate.fixtures`,
   `archgate.version` or `archgate.mavenCache` itself; no module excludes `archfixtures/**` itself.
3. `MavenFixtureIT`, `GradleFixtureFunctionalTest` and `ToolRunners` contain no Verifier or
   `GradleRunner` setup of their own.
4. Every public class under `*/src/main/java` of `core`, `context-*` and `gradle-plugin` is named in
   the `AGENTS.md` module map, and every class the map names exists in the package it states.
5. No `== null` / `!= null` in `gradle-plugin` and `testing/fixtures-support` main sources.

### Out of scope

- Removing `ArchUnitContexts`: it is public in the released 0.1.0 and
  `ArchUnitContextProvider.create` delegates to it; removing it is a breaking change.
- A shared "existing directories" helper for the two plugins: one line each, different input types
  (`String` in Maven, `File` in Gradle); a public helper in `core.launch` would grow the API for it.
- Any change to rules, contexts, `ReportFormatter` or plugin parameters.

## 2. Build (`build-logic`)

- **`archgate.rules-conventions`** (new precompiled plugin): configures `test` to depend on `jar`
  and sets `systemProperty 'archgate.rulesJar'` to the jar's absolute path, exactly as the three
  `rules-*/build.gradle` files do today. `rules-archunit`, `rules-javaparser` and `rules-classgraph`
  apply it and drop their copies.
- **`FixtureTests`** (new Groovy class next to `ArchgateVersions.groovy`): one static method that
  configures a `Test` task to run the shared fixtures:
  - `dependsOn rootProject.tasks.named('publishToItRepo')`;
  - inputs `itRepoArtifacts` (`PathSensitivity.NAME_ONLY`) and `fixtures`;
  - system properties `archgate.itRepo`, `archgate.fixtures`, `archgate.version`;
  - `testLogging { events 'failed'; exceptionFormat = 'full' }`;
  - when Maven is involved, additionally `maven.home` and `archgate.mavenCache`, and a dependency
    on `:maven-plugin:unpackMaven`.

  `maven-plugin` (`mavenIT`), `gradle-plugin` (`functionalTest`) and `testing/parity` (`test`) call
  it; each keeps only its own `description`, source set and extra dependencies.
- **Checkstyle:** `archgate.java-conventions` excludes `archfixtures/**` from `checkstyleTest` for
  every module. Fixtures are deliberately non-conforming inputs for rules. The three per-module
  exclusions are removed.
- **`gradle-plugin/build.gradle`:** one description string instead of two near-identical ones.

## 3. Shared test runners (`testing/fixtures-support`)

- **`MavenRun`**: builds the `Verifier` for a written fixture project the way `MavenFixtureIT` and
  `ToolRunners` do today (`ForkedMaven.pinJavaHome`, local repository, `log-run-<n>.txt`,
  `-s settings.xml -Pit-repo <cache tail> verify`, cache promotion) and returns the outcome the two
  callers already consume.
- **`GradleRun`**: runs a written fixture project with TestKit (`check --stacktrace`,
  `withPluginClasspath`) and returns the `BuildResult` or failure the callers consume.
- `fixtures-support` gains `implementation gradleTestKit()`. The module is not published.
- `MavenFixtureIT`, `GradleFixtureFunctionalTest` and `parity/ToolRunners` delegate to them.
  Assertions and expectations stay in the callers.

## 4. Test classes

- `core/src/test/java/dev/quokkify/archgate/context/archunit/ArchUnitContext.java` becomes
  `FakeArchUnitContext` in the same package. The package must stay: `ContextResolver` derives the
  `archgate-context-archunit` hint from it. Only the simple name changes, so it no longer shadows
  the real class.
- `rules-classgraph/.../ContextResolverIntegrationTest.java` moves to `context-classgraph` tests
  (package `dev.quokkify.archgate.context.classgraph`), which already have `core` on the test
  classpath. Any fixture it needs moves with it.

## 5. Documentation and hygiene

- **`AGENTS.md`**
  - Module map matches the tree: add `core.SimpleProjectLayout`, `core.launch.ModuleDirs`,
    `core.launch.ReportSink`, `context.archunit.ArchUnitContexts`, `DirectoryArchUnitContext`,
    `DirectoryJavaParserContext`, `DirectoryClassGraphContext`, and the `gradle-plugin` classes
    `ExistingDirectories`, `FailureMessage`, `GateWorkParameters`; state that `ArchRuleChecks` and
    `GeneratedCode` live in `context.archunit.support`; list `BomCompletenessTest` in
    `testing/fixtures-support`; name `archgate.rules-conventions` and `FixtureTests` in `build-logic`.
  - Dependency direction notes that test source sets of `context-*` and `rules-*` may depend on
    `core`.
  - Bilingual rule: `docs/ci-kit.md` is toolkit-owned and English only, like ADRs; specs in
    `docs/specs/` are English only.
  - Mentions `.ci/` and `.copier-answers*.yml` (ci-kit owned) and `docs/specs/`.
- `.copier-answers.core.yml`: `project_name: archgate`.
- `Objects.isNull` / `Objects.nonNull` instead of `== null` / `!= null` in `gradle-plugin`
  (`ArchgateElements`) and `testing/fixtures-support` main sources.
- Delete `docs/plans/2026-10-21-archgate-gradle.md`, a finished agent checklist; set the status line
  of `docs/specs/2026-10-21-archgate-gradle-design.md` to "approved design; implemented in 0.1.0".

## 6. Order of work

One commit per step on branch `chore/structure-cleanup`, each ending with a green `./gradlew check`:

1. `build: add rules conventions and fixture test helper`
2. `test: share Maven and Gradle fixture runners`
3. `test: rename core context fake and move context resolver integration test`
4. `docs: align AGENTS.md with the code and tidy repository files`

Commit types are `build`, `test` and `docs`, so Release Please cuts no release for them.
