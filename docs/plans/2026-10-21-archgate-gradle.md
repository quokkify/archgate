# ArchGate on Gradle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port ArchGate into `quokkify/archgate` as a Gradle-built project that ships a Maven plugin and a Gradle plugin over one engine, with ArchUnit, JavaParser and ClassGraph as separate analysis backends.

**Architecture:** `api` holds the contract only (rule, context, provider SPI) and has no dependencies. `core` loads rules and context providers through `ServiceLoader`, runs the rules, and formats the report. `GateLauncher` is the single entry both build plugins call, so each plugin only translates its build-tool concepts into a `LaunchRequest`. Each backend is a `context-*` module (provider plus context) paired with a `rules-*` module.

**Tech Stack:** Java 21, Gradle 9.7.1 (wrapper), JUnit Jupiter 6, AssertJ, ArchUnit 1.5.1, JavaParser 3.28.2, ClassGraph 4.8.x (see Task 13), Maven plugin API 3.9.0, `org.gradlex.maven-plugin-development` 1.0.3, Gradle TestKit, `org.apache.maven.shared:maven-verifier` 2.0.0, `com.vanniktech.maven.publish` 0.37.0, `com.gradle.plugin-publish` 2.2.1, Spotless 8.x with palantir-java-format, Checkstyle, JaCoCo; CI from `quokkify/ci-kit` v3.3.0 (Copier).

**Spec:** `docs/specs/2026-10-21-archgate-gradle-design.md`

**Status:** pending approval

`$SOURCE` is the source repository root. `<source package>` is its package path (the old package with `/` separators). The old package name is never written into this repository.

## Global Constraints

- License Apache-2.0. groupId `dev.quokkify.archgate`. Gradle plugin id `dev.quokkify.archgate`. Packages are `dev.quokkify.archgate.*`.
- Java 21 toolchain. Gradle wrapper 9.7.1.
- Artifact ids:
  - `archgate-api`, `archgate-core`;
  - `archgate-context-archunit`, `archgate-context-javaparser`, `archgate-context-classgraph`;
  - `archgate-rules-archunit`, `archgate-rules-javaparser`, `archgate-rules-classgraph`;
  - `archgate-maven-plugin`, `archgate-gradle-plugin`, `archgate-bom`.
- Gradle project paths match the spec module names: `:api`, `:core`, `:context-archunit`, …, `:bom`. Each project's `base.archivesName` is `archgate-<module>`.
- Dependency directions: `rules-X → api, context-X`; `context-X → api, <library X>`; `core → api`; plugins `→ core`. `api` has no dependencies at all. Nothing depends on `rules-*`.
- Published `rules-*` jars contain no `META-INF/services` file. A `context-*` jar contains exactly one services file: `META-INF/services/dev.quokkify.archgate.api.ContextProvider`.
- The consumer services file is `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`.
- `failOn` accepts `INFO | WARNING | ERROR | NEVER`, default `WARNING`. `UnableToVerifyError` always fails the build.
- Every published jar manifest carries `Archgate-Api-Version`. Release builds stamp the release version; any `-SNAPSHOT` build stamps `dev-SNAPSHOT`.
- Minimum Maven for consumers is 3.9.0: `maven-core` and `maven-plugin-api` stay `compileOnly` at 3.9.0.
- Dependencies are declared only in `gradle/libs.versions.toml`, and the catalog contains only what the build uses.
- No logging framework in `api`, `core`, `context-*` or `rules-*`. Only the plugins log, through Maven `Log` or the Gradle `Logger`.
- Repository hygiene: no former-host name, internal host, registry URL, ticket key or absolute machine path in any committed file.
- Every change reaches `main` through a pull request. The only exception is the Task 0 bootstrap commit.

## Review Focus

1. **Consumer has a different ArchUnit version on the test classpath than `context-archunit` was built against.** Expected: the rules run on the consumer's ArchUnit, because core no longer carries ArchUnit; no `LinkageError`. Test: Task 9 `differentArchUnitOnConsumerClasspathStillRuns`.
2. **Rule class lives in the gate module's `src/test` and extends a built-in rule.** Expected: `RegistrationCheck` requires it to be listed, as the source did. Test: Task 4 `unregisteredSubclassOfRegisteredRuleFails`, ported.
3. **Source dir configured but absent (no `src/test/java`), with a JavaParser rule that needs `TEST`.** Expected: `UnableToVerifyError` "No Java source under … was found", not a silent pass. Test: Task 12 `missingTestSourcesIsUnableToVerify`.
4. **Gradle run with `--configuration-cache` where `rootPackage` comes from a `-P` property.** Expected: the cache is stored and then reused. Test: Task 9 `configurationCacheIsReusedWithPropertyOverride`.
5. **Consumer lists the same rule twice, in two services files (main jar and test resources).** Expected: it runs once. Test: Task 4 `duplicateRegistrationAcrossTwoServicesFilesRunsOnce`, ported.

---

## Execution order and parallelism

```
T0 bootstrap (owner STOP) → T1 spike → T2 skeleton → T3 api → T4 core → T5 GateLauncher
T5 → T6 archunit ──┬─→ T7 fixtures ─┬─→ T8 maven-plugin ───────────┐
                   │                └─→ T9 gradle-plugin → T10 multi-module ─┴─→ T11 parity
T5 → T12 javaparser   (parallel with T6..T10)
T5 → T13 classgraph   (parallel with T6..T10, T12)
T6 → T14 docs + ADRs  (parallel with T7..T13)
T11, T12, T13 → T15 bom + publishing + CI → T16 release 0.1.0 (owner actions)
```

- Each task is one branch `task/NN-<slug>` off the current `main` and one PR. The PR merges only after `./gradlew build` is green.
- Parallel tasks touch disjoint modules. When a parallel task lands, its siblings rebase.
- The only shared file is `settings.gradle`: every new module adds one `include` line, so a rebase conflict there is trivial.

---

### Task 0: Bootstrap new `main` — STOP: requires explicit owner confirmation before running

This task force-replaces remote `main` and later deletes `feat/archgate-plugin-migration`. Both are irreversible for the remote history. Run it only after the owner has confirmed this exact task in the current conversation.

**Files:**
- Create: `LICENSE` (Apache License 2.0 full text, `Copyright 2026 quokkify`)
- Create: `README.md` (stub: title, one-line description, "work in progress", license line)
- Create outside the repo: `.git/info/archgate-denylist` (never committed; see Step 1)
- Create: `scripts/pre-push-denylist.sh` (committed in Task 2, installed here)

- [ ] **Step 1: Create the local denylist.** Put one case-insensitive ERE per line in `.git/info/archgate-denylist`:
  - the former host's company name;
  - its GitLab host;
  - its registry hosts;
  - its ticket-key prefix;
  - the name of its repository-manager product;
  - the home-directory path prefix of the local machine.

  The file lives under `.git/`, so it is never committed.
- [ ] **Step 2: Create an orphan branch `main-new` with only `LICENSE` and `README.md`.** Run `git grep -niEf .git/info/archgate-denylist` on it. Expected: no output.
- [ ] **Step 3: Owner confirms.** Then run `git push --force origin main-new:main`, followed by `gh repo edit quokkify/archgate --default-branch main`.
- [ ] **Step 4: Verify.** `gh api repos/quokkify/archgate --jq .default_branch` prints `main`. `git log --oneline origin/main` shows exactly one commit.
- [ ] **Step 5: Land the design docs via PR.** Rebase `design/archgate-gradle` (spec + this plan) onto the new `main` and open PR "docs: archgate design and plan".
- [ ] **Step 6: Owner confirms again.** Then run `git push origin --delete feat/archgate-plugin-migration`. Verify that `gh api repos/quokkify/archgate/branches --jq '.[].name'` lists neither `feat/archgate-plugin-migration` nor `main-new`.

---

### Task 1: Spike — `org.gradlex.maven-plugin-development` on Gradle 9.7.1 (throwaway)

**Question:** Can Gradle 9.7.1 build a Maven plugin jar with a correct descriptor and a `maven-plugin` POM, with the configuration cache on?

**Files:** a scratch directory outside the repository. Nothing from it is committed.

- [ ] **Step 1: Build the throwaway project.** It contains one module with:
  - `plugins { id 'java-library'; id 'maven-publish'; id 'org.gradlex.maven-plugin-development' version '1.0.3' }`;
  - `compileOnly 'org.apache.maven:maven-plugin-api:3.9.0'`, `compileOnly 'org.apache.maven:maven-core:3.9.0'`, `compileOnly 'org.apache.maven.plugin-tools:maven-plugin-annotations:3.16.0'`;
  - one `@Mojo(name = "hello", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)` with one `@Parameter(property = "hello.skip", defaultValue = "false")`;
  - `mavenPlugin { goalPrefix = 'hello' }`;
  - a `MavenPublication` with `pom.packaging = 'maven-plugin'`.
- [ ] **Step 2: Run** `./gradlew jar publishToMavenLocal --configuration-cache` twice.
  Expected:
  - both runs succeed, and the second prints `Reusing configuration cache.`;
  - the jar contains `META-INF/maven/plugin.xml` and `META-INF/maven/<group>/<artifact>/plugin-help.xml`;
  - `plugin.xml` contains `<requiredMavenVersion>3.9.0</requiredMavenVersion>` and `<goal>hello</goal>`;
  - the published POM has `<packaging>maven-plugin</packaging>`.
- [ ] **Step 3: Run the plugin from a Maven 3.9.16 project.** Use `mvn -q verify` on a one-pom project that binds `hello` and reads from the local repo. Expected: the mojo executes.
- [ ] **Step 4: Decide.**
  - **Pass** = Steps 2–3 hold. Task 8 uses the gradlex plugin.
  - **Fail** = any step fails. Task 8 uses the fallback:
    - a hand-written `maven-plugin/src/main/resources/META-INF/maven/plugin.xml` with a `${version}` token, expanded by `processResources`;
    - `maven-plugin-annotations` kept only for documentation;
    - `PluginDescriptorTest` extended to assert the `check` goal, its four parameters, `requiresDependencyResolution=test`, `threadSafe=true` and `requiredMavenVersion=3.9.0`.
  - Record the outcome and the reason in the Task 8 PR description.

---

### Task 2: Build skeleton, conventions, static checks, version handling

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*` (9.7.1), `gradlew`, `gradlew.bat`, `.gitignore`, `.gitattributes`, `version.txt` (`0.0.0`)
- Create: `build-logic/settings.gradle`, `build-logic/build.gradle`
- Create convention plugins in `build-logic/src/main/groovy/`:
  - `archgate.java-conventions.gradle`;
  - `archgate.publish-conventions.gradle`;
  - `archgate.it-repo.gradle`.
- Create: `build-logic/src/test/groovy/ApiVersionStampSpec.groovy`, or a JUnit test in `build-logic`. See Step 1.
- Create: `config/checkstyle/checkstyle.xml`, `scripts/pre-push-denylist.sh`, `AGENTS.md` (repo invariants; ported in Task 14, stub here)

**Interfaces:**
- Produces (used by every later task):
  - **`archgate.java-conventions`:**
    - `java-library`, toolchain 21, `options.release = 21`;
    - JUnit Platform;
    - Spotless (palantir-java-format, `removeUnusedImports`, license-header-free);
    - Checkstyle with `config/checkstyle/checkstyle.xml`;
    - JaCoCo with `jacocoTestCoverageVerification` at minimum `LINE` covered ratio `0.80`, wired into `check` only for projects whose path is `:api`, `:core`, `:context-*` or `:rules-*`;
    - jar manifest attributes `Archgate-Api-Version` (see Step 1), `Implementation-Title = archivesName`, `Implementation-Version = version`.
  - **`archgate.publish-conventions`:**
    - applies `com.vanniktech.maven.publish`: `publishToMavenCentral(automaticRelease = false)`, `signAllPublications()` only when the property `signingInMemoryKey` is present, `coordinates("dev.quokkify.archgate", base.archivesName.get(), version)`;
    - POM: name, description, url `https://github.com/quokkify/archgate`, license Apache-2.0, scm, developer `quokkify`;
    - also applies `archgate.it-repo`.
  - **`archgate.it-repo`:** adds a `publishing.repositories.maven { name = 'it'; url = rootProject.layout.buildDirectory.dir('it-repo') }` repository. That yields the task `publishAllPublicationsToItRepository`.
  - **Root task `publishToItRepo`:** depends on every `publishAllPublicationsToItRepository`.
  - **Project version** (root `build.gradle`, applied to all projects): `version.txt` content; append `-SNAPSHOT` unless `-Prelease=true` is set.

- [ ] **Step 1: Write the failing test.** `apiVersionStamp` in build-logic is a function `String apiVersionStamp(String version)`. Assertions:
  - `apiVersionStamp("0.1.0") == "0.1.0"`;
  - `apiVersionStamp("0.1.0-SNAPSHOT") == "dev-SNAPSHOT"`;
  - `apiVersionStamp("0.0.0-SNAPSHOT") == "dev-SNAPSHOT"`.
- [ ] **Step 2: Run** `./gradlew -p build-logic test`. Expected: FAIL, unresolved `apiVersionStamp`.
- [ ] **Step 3: Implement** `apiVersionStamp` in `build-logic/src/main/groovy/ArchgateVersions.groovy` and use it in the jar manifest of `archgate.java-conventions`.
- [ ] **Step 4: Write `config/checkstyle/checkstyle.xml`.**
  - `TreeWalker` modules: `AvoidStarImport`, `UnusedImports`, `RedundantImport`, `NeedBraces`, `EqualsHashCode`, `MissingSwitchDefault`, `FinalClass`, `HideUtilityClassConstructor`, `OneStatementPerLine`, `MultipleVariableDeclarations`, `IllegalImport` (default list).
  - `Checker`-level module: `FileTabCharacter`.
  - Formatting is Spotless's job, so no formatting modules.
- [ ] **Step 5: Write `scripts/pre-push-denylist.sh`.** It is POSIX `sh` and works as a git `pre-push` hook.
  - For each pushed `<local sha>`, it runs `git grep -niEf "$(git rev-parse --git-dir)/info/archgate-denylist" <sha>` and `git log -p <remote sha>..<local sha> | grep -niEf <list>`.
  - It exits 1 on any match.
  - It exits 1 with "denylist missing" when the file is absent.
  - Install it with `ln -s ../../scripts/pre-push-denylist.sh .git/hooks/pre-push`.
- [ ] **Step 6: Run** `./gradlew -p build-logic test && ./gradlew build`. Expected: PASS. The root build has no modules yet and succeeds.
- [ ] **Step 7: Run** `sh scripts/pre-push-denylist.sh origin git@github.com:quokkify/archgate.git < /dev/null`. Expected: exit 0.
- [ ] **Step 8: Commit** as `build: add Gradle skeleton, conventions and static checks`, then open a PR.

---

### Task 3: `api` — neutral contract

**Files:**
- Create in `api/src/main/java/dev/quokkify/archgate/api/`:
  - `ArchitectureRule.java`, `AnalysisContext.java`, `ProjectLayout.java`, `ContextProvider.java`;
  - `Severity.java`, `Scope.java`, `ArchitectureViolation.java`, `UnableToVerifyError.java`, `package-info.java`.
- Port: `$SOURCE/archgate-api/src/main/java/<source package>/api/{Severity,Scope,ArchitectureViolation,UnableToVerifyError}.java`. The bodies are unchanged; only the package changes.
- Test: `api/src/test/java/dev/quokkify/archgate/api/ArchitectureViolationTest.java`, ported from `$SOURCE/archgate-api/src/test/java/<source package>/api/ArchitectureViolationTest.java`. Add `ArchitectureRuleTest.java`.
- Create: `api/build.gradle` (`archgate.java-conventions`, `archgate.publish-conventions`, no dependencies)

**Interfaces:**
- Produces:
  ```java
  public interface ArchitectureRule<C extends AnalysisContext> {
    String name();
    Severity severity();
    default Set<Scope> scopes() { return EnumSet.of(Scope.MAIN, Scope.TEST); }
    Class<C> contextType();
    void verify(C context);
    default void checkViolations(String expectedContract, List<String> violations) // throws ArchitectureViolation when non-empty
  }
  public interface AnalysisContext { default Map<String, Duration> buildTimes() { return Map.of(); } }
  public interface ProjectLayout {
    String rootPackage();
    List<Path> classDirs(Scope scope);
    List<Path> sourceDirs(Scope scope);
    ClassLoader classLoader();
  }
  public interface ContextProvider<C extends AnalysisContext> {
    Class<C> type();
    C create(ProjectLayout layout);
  }
  ```
  `scopes()` and `checkViolations` keep the defaults from `$SOURCE/archgate-api/src/main/java/<source package>/api/ArchitectureRule.java`. `AnalysisContext.buildTimes()` carries the per-model timings that the source report printed (see Task 4).

- [ ] **Step 1: Write the failing tests.**
  - `ArchitectureRuleTest.defaultScopesAreMainAndTest`: a lambda-free anonymous rule; assert `scopes()` equals `Set.of(MAIN, TEST)`.
  - `ArchitectureRuleTest.checkViolationsThrowsWithContractAndCount`: `assertThatThrownBy(() -> rule.checkViolations("C", List.of("a","b"))).isInstanceOf(ArchitectureViolation.class).hasMessageContaining("C").hasMessageContaining("Violations (2):")`.
  - `ArchitectureRuleTest.checkViolationsWithEmptyListPasses`.
  - The ported `ArchitectureViolationTest`.
- [ ] **Step 2: Run** `./gradlew :api:test`. Expected: FAIL, compilation errors.
- [ ] **Step 3: Implement** the types above.
- [ ] **Step 4: Run** `./gradlew :api:build`. Expected: PASS. Also run `unzip -p api/build/libs/archgate-api-0.0.0-SNAPSHOT.jar META-INF/MANIFEST.MF | grep Archgate-Api-Version`. Expected: `Archgate-Api-Version: dev-SNAPSHOT`.
- [ ] **Step 5: Commit** as `feat(api): add backend-neutral rule and context contract`.

---

### Task 4: `core` — registry, compatibility, registration, runner, report, contexts

**Files:**
- Create: `core/build.gradle` (`api(project(':api'))` only)
- Port into `core/src/main/java/dev/quokkify/archgate/core/` from `$SOURCE/archgate-core/src/main/java/<source package>/core/`:
  - **Unchanged except the package:**
    - `ApiVersion.java` (`:1-78`);
    - `ApiVersionManifest.java` (`:1-56`);
    - `FailOn.java` (`:1-31`);
    - `GateResult.java` (`:1-29`).
  - **`ApiCompatibilityCheck.java` (`:1-92`).** Change only `remedy` at `:65-72`:
    - `"Raise the archgate plugin version to at least " + required + "."`;
    - dev: `"Rebuild these libraries against a released archgate-api, or run the archgate plugin " + ApiVersion.DEV + "."`.
  - **`RuleRegistry.java` (`:1-71`)**, element type `ArchitectureRule<?>`:
    - `SERVICES_FILE = "META-INF/services/dev.quokkify.archgate.api.ArchitectureRule"`;
    - replace `LIBRARY_RULES_PACKAGE`/`LIBRARY_RULES_HINT` (`:19-21`, used `:66-68`) with the regex `dev\.quokkify\.archgate\.rules\.(\w+)\.` → hint `"Add dev.quokkify.archgate:archgate-rules-<group 1> as a test dependency."`.
  - **`RegistrationCheck.java` (`:1-57`).** Reimplement without ArchUnit (see Step 3). Keep the message text at `:50-56`.
  - **`RuleOutcome.java`**: field `ArchitectureRule<?> rule`.
  - **`ReportFormatter.java` (`:1-104`).** Change `:41-46` only: the label `"Class import: "` becomes `"Model build: "`. Keys come from `GateResult.modelBuildTimes()`.
  - **`GateRunner.java` (`:1-107`).**
    - The signature becomes `run(List<ArchitectureRule<?>> rules, ContextResolver contexts)`.
    - Each rule calls `contexts.get(rule.contextType())` inside its own virtual thread, then `verify` with an unchecked cast through a private generic helper.
    - Drop the `ClassDirsContext.resetModelWait/modelWait` subtraction (`:70`, `:81`): the rule duration now includes model build time.
- Create:
  - `RootPackage.java`, moved from `$SOURCE/archgate-maven-plugin/src/main/java/<source package>/maven/RootPackage.java:1-22`. `validate` throws `UnableToVerifyError` with the same text.
  - `ContextResolver.java`.
  - `SimpleProjectLayout.java`: a record implementing `ProjectLayout`.
- Tests: port into `core/src/test/java/dev/quokkify/archgate/core/` from `$SOURCE/archgate-core/src/test/java/<source package>/core/`:
  - `ApiCompatibilityCheckTest`, `ApiVersionManifestTest`, `ApiVersionTest`, `FailOnTest`, `GateRunnerTest`, `RegistrationCheckTest`, `ReportFormatterTest`, `RuleRegistryTest`;
  - helpers `RuleJars`, `TestRules`;
  - all fixtures `archfixtures/{registry,version,context,other}`.
  - `ClassDirsContextTest` moves to Task 6.
  - Add `ContextResolverTest` and `RootPackageTest` (from `$SOURCE/archgate-maven-plugin/src/test/java/<source package>/maven/RootPackageTest.java`).

**Interfaces:**
- Consumes: Task 3 types.
- Produces:
  ```java
  public final class RuleRegistry { public static final String SERVICES_FILE; public static List<ArchitectureRule<?>> load(ClassLoader loader); }
  public final class RegistrationCheck { public static void verify(List<Path> ownClassDirs, List<ArchitectureRule<?>> registered, ClassLoader loader); }
  public final class ContextResolver implements AutoCloseable {
    public ContextResolver(ProjectLayout layout, ClassLoader loader);
    public <C extends AnalysisContext> C get(Class<C> type);   // lazy, at most once per type, thread-safe
    public Map<String, Duration> buildTimes();                 // merged buildTimes() of created contexts
    public void close();                                        // closes AutoCloseable contexts; failures added as suppressed to one UnableToVerifyError
  }
  public final class GateRunner { public static GateResult run(List<ArchitectureRule<?>> rules, ContextResolver contexts); }
  public final class ReportFormatter { public static List<String> format(GateResult result, Optional<Severity> failOn); public static String counts(GateResult result); }
  public final class FailOn { public static final Severity DEFAULT; public static Optional<Severity> parse(String value); }
  public final class RootPackage { public static String validate(String value); }
  public record SimpleProjectLayout(String rootPackage, List<Path> mainClassDirs, List<Path> testClassDirs,
                                    List<Path> mainSourceDirs, List<Path> testSourceDirs, ClassLoader classLoader) implements ProjectLayout
  public record GateResult(List<RuleOutcome> outcomes, Map<String, Duration> modelBuildTimes, long elapsedMillis)
  ```

- [ ] **Step 1: Port the tests and write the new ones.**
  - Mechanical edits to the ported tests: package rename; `ArchitectureRule` → `ArchitectureRule<?>`; `TestRules` implements `ArchitectureRule<TestRules.NoContext>` with `record NoContext() implements AnalysisContext`. Tests that called `GateRunner.run(rules, ClassDirsContext)` pass a `ContextResolver` built over a layout whose loader provides a `ContextProvider<NoContext>` (test services file `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ContextProvider` → `dev.quokkify.archgate.core.TestRules$NoContextProvider`).
  - The test `classImportTimesAreSummarized` becomes `modelBuildTimesAreSummarized`, expecting the line `"Model build: all 3 ms, main 5 ms"` for the times map `{main=5ms, all=3ms}`.
  - `ContextResolverTest`:
    - `missingProviderIsUnableToVerifyWithHint`: type `dev.quokkify.archgate.context.archunit.ArchUnitContext` absent; message contains `"No ContextProvider for dev.quokkify.archgate.context.archunit.ArchUnitContext"` and `"dev.quokkify.archgate:archgate-context-archunit"`.
    - `missingProviderForForeignTypeHasNoArtifactHint`: type `com.example.CustomContext`; message contains `"No ContextProvider for com.example.CustomContext"` and does not contain `"archgate-context-"`.
    - `contextIsCreatedLazilyAndOnce`: a counting provider; no `get` means 0 creations; 50 concurrent `get` calls on virtual threads mean 1 creation.
    - `autoCloseableContextIsClosed`: after `close()` the context's `closed` flag is true.
    - `twoProvidersForOneTypeAreUnableToVerify`: message contains both provider class names.
    - `buildTimesMergeCreatedContexts`.
  - `RegistrationCheckTest`, ported (`unregisteredConcreteRuleFails`, `unregisteredSubclassOfRegisteredRuleFails`, `abstractPackagePrivateAndArgConstructorRulesAreIgnored`, `registeredLibraryRulesNeedNotLiveInOwnDirs`). Add `moduleInfoAndPackageInfoAreIgnored`.
  - `RuleRegistryTest.missingLibraryClassNamesDependencyHint`: the services file lists `dev.quokkify.archgate.rules.archunit.NoThreadSleepRule` (absent); message contains `"Add dev.quokkify.archgate:archgate-rules-archunit as a test dependency."`.
- [ ] **Step 2: Run** `./gradlew :core:test`. Expected: FAIL, compilation.
- [ ] **Step 3: Implement.**
  - **`RegistrationCheck.verify`**:
    1. walk `*.class` files under each existing dir;
    2. skip `module-info`, `package-info` and names containing `$`;
    3. `Class.forName(name, false, loader)` (a `LinkageError` → skip);
    4. keep classes that are `ArchitectureRule.class.isAssignableFrom(c)`, not interface, `Modifier.isPublic`, not abstract, and have a public no-arg constructor (the same predicate as the source `:40-48`).
  - **`ContextResolver`**: `ServiceLoader.load(ContextProvider.class, loader)` indexed by `type()`. Per-type `FutureTask` in a `ConcurrentHashMap` for once-only creation. Time `create()`.
- [ ] **Step 4: Run** `./gradlew :core:build`. Expected: PASS, including JaCoCo ≥ 0.80. Also run `./gradlew :core:dependencies --configuration runtimeClasspath`. Expected: only `project :api`.
- [ ] **Step 5: Commit** as `feat(core): port engine with context resolver and ArchUnit-free registration check`.

---

### Task 5: `GateLauncher` — the shared entry for both plugins

**Files:**
- Create in `core/src/main/java/dev/quokkify/archgate/core/launch/`:
  - `GateLauncher.java`, `LaunchRequest.java`, `ModuleDirs.java`;
  - `ReportSink.java`, `GateFailure.java`.
- Logic source: `$SOURCE/archgate-maven-plugin/src/main/java/<source package>/maven/CheckMojo.java`:
  - `:74-94` — class loader, context class loader swap, close;
  - `:96-104` — decide and failure message;
  - `:106-110` — evaluate;
  - `:142-153` — log level by worst severity.
- Test: `core/src/test/java/dev/quokkify/archgate/core/launch/GateLauncherTest.java`

**Interfaces:**
- Produces:
  ```java
  public record ModuleDirs(List<Path> mainClasses, List<Path> testClasses, List<Path> mainSources, List<Path> testSources)
  public record LaunchRequest(String rootPackage, String failOn, ModuleDirs dirs, List<Path> ownDirs,
                              List<Path> ruleClasspath, Optional<Path> reportFile)
  @FunctionalInterface public interface ReportSink { void line(Severity level, String text); }
  public final class GateFailure extends RuntimeException { public GateResult result(); }
  public final class GateLauncher {
    public static GateResult run(LaunchRequest request, ReportSink sink); // throws GateFailure, UnableToVerifyError
  }
  ```
  - **Order:**
    1. `RootPackage.validate`;
    2. `FailOn.parse`;
    3. `URLClassLoader(ruleClasspath, GateLauncher.class.getClassLoader())`, set as the thread context class loader;
    4. `RuleRegistry.load`;
    5. `RegistrationCheck.verify(ownDirs, rules, loader)`;
    6. `GateRunner.run` with `new ContextResolver(layout, loader)` (try-with-resources);
    7. report every line via `sink` at level `worst().orElse(INFO)`;
    8. write `reportFile` (UTF-8, `\n`-joined, parent dirs created);
    9. if `failsOn`, throw `GateFailure("Architecture gate failed: %s; failure policy: failOn=%s")`, the text from source `:99-102`;
    10. `finally` restore the context class loader and close the loader. A close failure is reported as an `INFO` sink line `"Cannot close the test classpath loader: <msg>"`.
  - `UnableToVerifyError` propagates and no report file is written.

- [ ] **Step 1: Write the failing tests.** Use `RuleJars` from Task 4 and the `NoContext` provider.
  - `passingRulesReturnResultAndWriteReport`: the report file exists and its last-but-policy line starts with `"Gate PASSED: 1 rule, no findings"`.
  - `warningAtDefaultThresholdThrowsGateFailureAfterReporting`: the sink received lines at `WARNING`; the `GateFailure` message equals `"Architecture gate failed: 1 rule, 1 warning; failure policy: failOn=WARNING"`.
  - `failOnErrorLetsWarningPass`.
  - `failOnNeverNeverThrows`.
  - `invalidRootPackageIsUnableToVerify`.
  - `contextClassLoaderIsRestoredAfterFailure`.
  - `unableToVerifyWritesNoReport`.
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*GateLauncherTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `GateLauncher.run` per the order above.
- [ ] **Step 4: Run** `./gradlew :core:build`. Expected: PASS.
- [ ] **Step 5: Commit** as `feat(core): add GateLauncher shared by the build plugins`.

---

### Task 6: `context-archunit` + `rules-archunit` (14 rules, behaviour unchanged)

**Files:**
- Create: `context-archunit/build.gradle` (`api(project(':api'))`, `api(libs.archunit)`)
- Create in `context-archunit/src/main/java/dev/quokkify/archgate/context/archunit/`:
  - `ArchUnitContext.java`, `ArchUnitContextProvider.java`;
  - `support/ArchRuleChecks.java`, `support/GeneratedCode.java`.
- Port:
  - `$SOURCE/archgate-core/src/main/java/<source package>/core/ClassDirsContext.java:23-141` → the `ArchUnitContext` implementation. Drop `MODEL_WAIT_NANOS`, `resetModelWait` and `modelWait` (`:29`, `:105-111`, `:133-140`). Keep the empty-model `UnableToVerifyError` text (`:118-122`). The model keys `main`, `test`, `all` are returned by `buildTimes()`.
  - `$SOURCE/archgate-api/src/main/java/<source package>/api/support/{ArchRuleChecks,GeneratedCode}.java` → `support/`, unchanged.
- Create: `context-archunit/src/main/resources/META-INF/services/dev.quokkify.archgate.api.ContextProvider` (one line: `dev.quokkify.archgate.context.archunit.ArchUnitContextProvider`)
- Tests:
  - `$SOURCE/archgate-core/src/test/java/<source package>/core/ClassDirsContextTest.java` → `ArchUnitContextTest`;
  - `$SOURCE/archgate-api/src/test/java/<source package>/api/support/{ArchRuleChecksTest,GeneratedCodeTest}.java` and the `archfixtures/{console,generated}` fixtures.
- Create: `rules-archunit/build.gradle` (`api(project(':context-archunit'))`; test deps `querydsl-core 5.1.0`, `awaitility 4.3.0`, `log4j-api 2.26.1`, `testng 7.12.0`, `slf4j-api`, JUnit, AssertJ — exactly the test deps of `$SOURCE/archgate-rules/pom.xml`)
- Port: the 14 rules from `$SOURCE/archgate-rules/src/main/java/<source package>/rules/*.java` → `rules-archunit/src/main/java/dev/quokkify/archgate/rules/archunit/`:
  - `ClassLocationBySuffixRule`, `HashCodeEqualsTogetherRule`, `LoggerFieldNameRule`, `MaxMethodParametersRule`, `NoConsoleOutputRule`, `NoDeprecatedApiUsageRule`, `NoDisabledTestsRule`;
  - `NoGenericThrowsRule`, `NoJavaUtilDateRule`, `NoThreadSleepRule`, `ServiceRegistrationRule`, `TestAssertionsRule`, `TestClassNamingRule`, `UtilityClassFinalityRule`.
- Port: all 14 `*RuleTest.java`, `FixtureContexts.java`, and every `archfixtures/*` package from `$SOURCE/archgate-rules/src/test/java/`.

**Interfaces:**
- Consumes: `ProjectLayout`, `ContextProvider`, `AnalysisContext` (Task 3).
- Produces:
  ```java
  public interface ArchUnitContext extends AnalysisContext {
    String rootPackage();
    JavaClasses main();
    JavaClasses test();
    JavaClasses all();
    ProjectLayout layout();
  }
  public final class ArchUnitContextProvider implements ContextProvider<ArchUnitContext>
  public final class ArchUnitContexts { public static ArchUnitContext of(ProjectLayout layout); }  // test & provider factory
  ```
  `layout()` exists because `ServiceRegistrationRule.verify` reads `classDirs` (source `ServiceRegistrationRule.java:50-51`).

- [ ] **Step 1: Port the tests with mechanical edits only.**
  - `RuleContext` → `ArchUnitContext`;
  - `mainClasses()` → `main()`, `testClasses()` → `test()`;
  - `context.classDirs(s)` → `context.layout().classDirs(s)`;
  - `FixtureContexts.of(pkg)` returns `ArchUnitContexts.of(new SimpleProjectLayout(pkg, dirs, dirs, List.of(), List.of(), loader))`. `SimpleProjectLayout` comes from `:core` as a `testImplementation` only.
  - Add a test to every rule test class: `contextTypeIsArchUnit` — `assertThat(new XRule().contextType()).isEqualTo(ArchUnitContext.class)`.
  - Add `rules-archunit/src/test/java/.../NoServicesFileShippedTest.noServicesFileInJar`: open `tasks.jar` output (path via system property `archgate.rulesJar` set in `build.gradle`) and assert there is no entry under `META-INF/services/`.
- [ ] **Step 2: Run** `./gradlew :context-archunit:test :rules-archunit:test`. Expected: FAIL, compilation.
- [ ] **Step 3: Implement.** Each rule changes only its signature: `implements ArchitectureRule<ArchUnitContext>`, `contextType()` returns `ArchUnitContext.class`, `verify(ArchUnitContext context)`. Rule names, severities, scopes, contract texts and `definition()` bodies stay byte-for-byte the same as in the source. Ordering in `verify` is unchanged.
- [ ] **Step 4: Run** `./gradlew :context-archunit:build :rules-archunit:build`. Expected: PASS. Every ported assertion is unchanged apart from the renames.
- [ ] **Step 5: Commit** as `feat: add ArchUnit context and port the 14 built-in rules`.

---

### Task 7: Shared fixtures and fixture support

**Files:**
- Create: `fixtures/<scenario>/` for each of `pass`, `fail-on-threshold`, `skip`, `missing-services`, `unregistered-rule`, `multi-module`, `multi-module-violation`, `incompatible-rule-library`, `missing-context-provider`.
- Port sources only (no `pom.xml`, `invoker.properties` or `verify.groovy`) from `$SOURCE/archgate-maven-plugin/src/it/<scenario>/`:
  - `pass` ← `single-module`;
  - the rest have the same names.
  - Services files are renamed to `META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`. Their entries become `dev.quokkify.archgate.rules.archunit.NoThreadSleepRule` and `dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule`.
  - The custom rules (`NoLegacyClassesRule`, `UnregisteredRule`, `LibraryRule`) implement `ArchitectureRule<ArchUnitContext>`.
- Create:
  - `fixtures/missing-context-provider/`: `src/main/java/com/example/Greeter.java`; `src/test/java/com/example/CustomContext.java` (`implements AnalysisContext`); `src/test/java/com/example/CustomRule.java` (`ArchitectureRule<CustomContext>`, name `"Custom rule"`); a services file listing `com.example.CustomRule`.
  - In each scenario: `fixture.properties` and `expected.properties`.
  - `testing/fixtures-support/build.gradle` (`java-library`, not published), with `src/main/java/dev/quokkify/archgate/testing/{Fixture,FixtureSpec,Expectation,BuildTool,MavenProjectWriter,GradleProjectWriter}.java`.
- Test: `testing/fixtures-support/src/test/java/dev/quokkify/archgate/testing/FixtureSpecTest.java`

**`fixture.properties` keys (exact):**
- `rootPackage` — e.g. `com.example`.
- `modules` — comma list; empty means single module.
- `gate` — the module that runs the check; empty when single-module.
- `dependsOn.<module>` — comma list of sibling modules, as Maven `compile` dependencies or Gradle `implementation project(...)`.
- `testDependencies` — comma list of archgate artifact ids added at test scope, e.g. `archgate-rules-archunit`.
- `ruleLibrary` — only for `incompatible-rule-library`: `rules-lib=not-a-version` means module `rules-lib` builds a jar `rules-lib-1.0.jar` with manifest `Archgate-Api-Version: not-a-version`, and `gate` depends on it.

**`expected.properties` keys (exact):**
- `runs` — number of runs.
- `run.N.args` — extra arguments: `-Darchgate.failOn=ERROR` for Maven, translated to `-Parchgate.failOn=ERROR` for Gradle.
- `run.N.outcome` — `success | failure`.
- `run.N.contains.M` — a substring the log must contain.
- `run.N.absent.M` — a substring the log must not contain.
- `run.N.skipped` — `true` for `skip`.

Expected values ported from the source `verify.groovy` files:

| Scenario | Outcome | Must contain |
|---|---|---|
| `pass` | success | `Gate PASSED: 2 rules, no findings`, `[PASS]`, `Failure policy (failOn=WARNING):` |
| `fail-on-threshold` | run 1 failure, run 2 (`failOn=ERROR`) success | run 1: `[WARNING]` |
| `skip` | success, skipped | Maven: `archgate: skipped` |
| `missing-services` | failure | `no rules registered` |
| `unregistered-rule` | failure | `com.example.UnregisteredRule`, `META-INF/services/dev.quokkify.archgate.api.ArchitectureRule` |
| `multi-module` | success | `Gate PASSED: 2 rules, no findings`, `No legacy classes` |
| `multi-module-violation` | failure | `No legacy classes`, `com.example.api.LegacyAccount`, `com.example.tests.LegacyFlow`, `Gate FAILED: 2 rules, 1 error`, `Failure policy (failOn=WARNING):` |
| `incompatible-rule-library` | failure | `rules-lib-1.0.jar declares Archgate-Api-Version 'not-a-version', expected X.Y.Z or dev-SNAPSHOT`; absent `Gate PASSED` |
| `missing-context-provider` | failure | `No ContextProvider for com.example.CustomContext` |

**Interfaces:**
- Produces:
  ```java
  public enum BuildTool { MAVEN, GRADLE }
  public record FixtureSpec(String rootPackage, List<String> modules, Optional<String> gate,
                            Map<String, List<String>> dependsOn, List<String> testDependencies,
                            Map<String, String> ruleLibraries)  { public static FixtureSpec load(Path fixtureDir); }
  public record Expectation(int run, List<String> args, boolean success, boolean skipped,
                            List<String> contains, List<String> absent) { public static List<Expectation> load(Path fixtureDir); }
  public final class Fixture {
    public static Path root();                                  // system property archgate.fixtures
    public static List<String> scenarios();                     // directory names, sorted
    public static Path materialize(String scenario, BuildTool tool, Path target, Path itRepo, String archgateVersion);
  }
  ```
  - **`MavenProjectWriter`**:
    - Writes a parent `pom.xml` (packaging `pom`, `<modules>`) and per-module poms with groupId `com.example`, version `1.0`, `maven.compiler.release=21`.
    - The gate module binds `dev.quokkify.archgate:archgate-maven-plugin:${archgateVersion}`, goal `check`, `<rootPackage>`.
    - Writes `settings.xml` with repository and pluginRepository `it-local` = `itRepo.toUri()`. The shape mirrors `$SOURCE/archgate-maven-plugin/src/it/settings.xml`.
  - **`GradleProjectWriter`**:
    - `settings.gradle` with `include`s, `dependencyResolutionManagement.repositories { maven { url = '<itRepo>' }; mavenCentral() }` and `rootProject.name = '<scenario>'`.
    - Per-module `build.gradle`: `id 'java-library'`; the gate applies `id 'dev.quokkify.archgate'` with `archgate { rootPackage = '…'; modules(<':'+dependsOn of gate>) }`; every module in `dependsOn.<gate>` also applies `dev.quokkify.archgate`, so it exposes `archgateElements` (Task 10).
    - Toolchain 21.
    - `ruleLibrary` → `jar { manifest { attributes('Archgate-Api-Version': '<value>') } }` and `archivesName = 'rules-lib'`, `version = '1.0'`.

- [ ] **Step 1: Write the failing tests.**
  - `FixtureSpecTest.loadsMultiModuleViolation`: modules `[api, tests, architecture]`, gate `architecture`, `dependsOn.architecture == [api, tests]`.
  - `FixtureSpecTest.everyScenarioHasExpectations`: for each of `Fixture.scenarios()`, `Expectation.load` is non-empty, and the scenario list contains the nine names above (later tasks add more).
  - `MavenProjectWriterTest.gatePomBindsCheckGoal`.
  - `GradleProjectWriterTest.gateBuildFileListsModules`: contains `modules(':api', ':tests')`.
- [ ] **Step 2: Run** `./gradlew :testing:fixtures-support:test`. Expected: FAIL.
- [ ] **Step 3: Implement** the records and writers. The writers use plain text blocks, not a template engine.
- [ ] **Step 4: Run** `./gradlew :testing:fixtures-support:build`. Expected: PASS.
- [ ] **Step 5: Commit** as `test: add shared plugin fixtures and fixture writers`.

---

### Task 8: `maven-plugin` + `mavenIT`

**Files:**
- Create: `maven-plugin/build.gradle`:
  - `archgate.java-conventions`, `archgate.publish-conventions`;
  - `org.gradlex.maven-plugin-development` 1.0.3, or the Task 1 fallback;
  - `implementation(project(':core'))`;
  - `compileOnly` maven-plugin-api/maven-core 3.9.0;
  - `compileOnly` maven-plugin-annotations 3.16.0;
  - POM packaging `maven-plugin`.
- Port into `maven-plugin/src/main/java/dev/quokkify/archgate/maven/` from `$SOURCE/archgate-maven-plugin/src/main/java/<source package>/maven/`:
  - `ModuleSelection.java` (`:1-50`, unchanged);
  - `MainDir.java` (`:1-26`, unchanged);
  - `CheckMojo.java` (`:36-59` annotations and parameters unchanged; the body rewritten).
- Delete from the port: `RootPackage.java` (moved to core in Task 4).
- Tests: port `MainDirTest`, `ModuleSelectionTest`, `PluginDescriptorTest` (`DESCRIPTOR` path → the gradlex output dir, or `build/resources/main/META-INF/maven/plugin.xml` for the fallback).
- Create: source set `mavenIT` in `maven-plugin/src/mavenIT/java/dev/quokkify/archgate/maven/it/MavenFixtureIT.java`.

**Interfaces:**
- Consumes:
  - `GateLauncher.run(LaunchRequest, ReportSink)`, `GateFailure`, `ModuleDirs` (Task 5);
  - `Fixture.materialize(..., BuildTool.MAVEN, ...)`, `Expectation` (Task 7).
- Produces: goal `check`, parameters `rootPackage` (property `archgate.rootPackage`, required), `failOn` (`archgate.failOn`, default `WARNING`), `modules`, `skip` (`archgate.skip`). Report file `${project.build.directory}/archgate/report.txt`.
- **`CheckMojo.execute`**:
  - `skip` → `getLog().info("archgate: skipped")`.
  - Otherwise build a `LaunchRequest`:
    - dirs from `ModuleSelection.select` + `MainDir.resolve` (source `CheckMojo.java:77-83`);
    - `mainSources` = each module's `getCompileSourceRoots()` that exist, `testSources` = `getTestCompileSourceRoots()` that exist;
    - `ownDirs` per source `:120-128`;
    - `ruleClasspath` = `getTestClasspathElements()`.
  - Sink → `getLog().error/warn/info` by level.
  - `GateFailure` → `MojoFailureException(message)`; `UnableToVerifyError` → `MojoExecutionException(message, error)`.

- [ ] **Step 1: Port the unit tests.** Add `CheckMojoSourcesTest.missingSourceRootsAreDropped`, which uses a stub `MavenProject` with one existing and one missing root.
- [ ] **Step 2: Write the IT harness.**
  - A configuration `mavenDistribution` = `org.apache.maven:apache-maven:3.9.16:bin@zip`.
  - Task `unpackMaven` (`Sync` from `zipTree`) → `build/maven`.
  - The `mavenIT` test task:
    - depends on `unpackMaven` and `:publishToItRepo`;
    - sets the system properties `maven.home`, `archgate.itRepo`, `archgate.fixtures`, `archgate.version`;
    - is wired into `check`.
  - `MavenFixtureIT` is a `@ParameterizedTest @MethodSource("scenarios")` over `Fixture.scenarios()`:
    1. materialize into `@TempDir`;
    2. for each `Expectation`, `new Verifier(dir.toString())`, `setLocalRepo(<tempdir>/m2)`, `addCliArguments("-s", "settings.xml", "-Pit-repo", "verify", <args>)`;
    3. `execute()` expecting success or a `VerificationException`;
    4. assert `contains`/`absent` against `verifier.loadLogContent()`.
- [ ] **Step 3: Run** `./gradlew :maven-plugin:test :maven-plugin:mavenIT`. Expected: FAIL (no mojo yet).
- [ ] **Step 4: Implement** `CheckMojo` per the Interfaces block.
- [ ] **Step 5: Run** `./gradlew :maven-plugin:build`. Expected: PASS, with all nine scenarios green and `requiresOnlyTheSupportedMinimumMavenVersion` green. Also run `unzip -l maven-plugin/build/libs/archgate-maven-plugin-*.jar | grep META-INF/maven/plugin.xml`. Expected: one line.
- [ ] **Step 6: Commit** as `feat(maven-plugin): add Maven plugin on GateLauncher with Gradle-run ITs`.

---

### Task 9: `gradle-plugin` (single module) + `functionalTest`

**Files:**
- Create: `gradle-plugin/build.gradle`:
  - `java-gradle-plugin`, `archgate.java-conventions`, `archgate.publish-conventions`, `com.gradle.plugin-publish` 2.2.1;
  - `implementation(project(':core'))`;
  - `gradlePlugin { website, vcsUrl, plugins { archgate { id = 'dev.quokkify.archgate'; implementationClass = 'dev.quokkify.archgate.gradle.ArchgatePlugin'; displayName; description; tags = ['architecture','archunit','quality'] } } }`.
- Create in `gradle-plugin/src/main/java/dev/quokkify/archgate/gradle/`:
  - `ArchgatePlugin.java`, `ArchgateExtension.java`, `ArchgateCheckTask.java`;
  - `GateWorkAction.java`, `GateWorkParameters.java`.
- Create: source set `functionalTest` in `gradle-plugin/src/functionalTest/java/dev/quokkify/archgate/gradle/{GradleFixtureFunctionalTest,CachingFunctionalTest}.java`

**Interfaces:**
- Consumes: Task 5 launcher types; Task 7 `Fixture`, `Expectation`, `BuildTool.GRADLE`.
- Produces:
  ```java
  public abstract class ArchgateExtension {
    public abstract Property<String> getRootPackage();
    public abstract Property<String> getFailOn();      // convention: providers.gradleProperty("archgate.failOn").orElse("WARNING")
    public abstract Property<Boolean> getSkip();       // convention: providers.gradleProperty("archgate.skip").map(v -> v.isEmpty() || Boolean.parseBoolean(v)).orElse(false)
    public abstract ListProperty<String> getModules();
    public void modules(String... projectPaths);
  }
  @CacheableTask public abstract class ArchgateCheckTask extends DefaultTask {
    @Input getRootPackage(); @Input getFailOn();
    @Classpath getRuleClasspath();                                         // testRuntimeClasspath + test output
    @InputFiles @PathSensitive(RELATIVE) getMainClassesDirs(); getTestClassesDirs(); getMainSourceDirs(); getTestSourceDirs();
    @Internal getOwnDirs();
    @OutputFile getReportFile();                                           // build/reports/archgate/report.txt
    @Inject protected abstract WorkerExecutor getWorkerExecutor();
  }
  ```
  - **Plugin wiring**:
    - `pluginManager.withPlugin('java')` registers `archgateCheck` (group `verification`, `dependsOn testClasses`), and `check.dependsOn archgateCheck`;
    - `onlyIf("archgate.skip is set") { !skip }`;
    - `-Parchgate.rootPackage` overrides the extension value.
  - **`ArchgateCheckTask` action**: `workerExecutor.classLoaderIsolation { classpath.from(<code sources of GateLauncher, ArchitectureRule, GateWorkAction>) }` → submit `GateWorkAction`.
  - **`GateWorkAction`**: calls `GateLauncher.run`. The sink maps `ERROR` → `logger.error`, `WARNING` → `logger.warn`, `INFO` → `logger.lifecycle`. `GateFailure` and `UnableToVerifyError` → `GradleException(message)`.
  - No `Project` access at execution time.

- [ ] **Step 1: Write the failing functional tests.**
  - `GradleFixtureFunctionalTest`, parameterized over `Fixture.scenarios()` minus `multi-module*` (Task 10 owns those):
    - `GradleRunner.create().withProjectDir(dir).withPluginClasspath().withArguments(["check", "--stacktrace"] + args)`, using `build()` or `buildAndFail()` per `Expectation`;
    - assert `contains`/`absent` on `getOutput()`;
    - for `skip`, assert `task(":archgateCheck").getOutcome() == SKIPPED`.
  - `CachingFunctionalTest` on fixture `pass`:
    - `secondRunIsUpToDate`: the second `check` gives `:archgateCheck` outcome `UP_TO_DATE`;
    - `configurationCacheIsReused`: two runs with `--configuration-cache`; the second output contains `Reusing configuration cache.`;
    - `configurationCacheIsReusedWithPropertyOverride`: the same with `-Parchgate.failOn=ERROR` on both runs;
    - `buildCacheHit`: `--build-cache` with a local cache dir in `@TempDir`, run, `clean`, run again; outcome `FROM_CACHE`;
    - `reportFileIsWritten`: `build/reports/archgate/report.txt` contains `Gate PASSED: 2 rules, no findings`.
  - `differentArchUnitOnConsumerClasspathStillRuns` (Review Focus 1): fixture `pass` plus `testImplementation 'com.tngtech.archunit:archunit:1.4.1'` forced via `resolutionStrategy.force`; the build succeeds.
  - The `functionalTest` task depends on `:publishToItRepo`, sets `archgate.itRepo`/`archgate.fixtures`/`archgate.version`, and is wired into `check`.
- [ ] **Step 2: Run** `./gradlew :gradle-plugin:functionalTest`. Expected: FAIL (plugin id not found).
- [ ] **Step 3: Implement** the plugin, extension, task and work action per the Interfaces block.
- [ ] **Step 4: Run** `./gradlew :gradle-plugin:build`. Expected: PASS, with the seven single-module scenarios and all `CachingFunctionalTest` cases green.
- [ ] **Step 5: Commit** as `feat(gradle-plugin): add Gradle plugin with cacheable archgateCheck task`.

---

### Task 10: Gradle multi-module — `archgateElements`

**Files:**
- Modify: `gradle-plugin/src/main/java/dev/quokkify/archgate/gradle/ArchgatePlugin.java`
- Create: `gradle-plugin/src/main/java/dev/quokkify/archgate/gradle/ArchgateElements.java`
- Test: extend `GradleFixtureFunctionalTest` to include `multi-module` and `multi-module-violation`. Add `ArchgateElementsFunctionalTest`.

**Interfaces:**
- Produces:
  ```java
  final class ArchgateElements {
    static final Attribute<String> KIND = Attribute.of("dev.quokkify.archgate.kind", String.class);
    static final String MAIN_CLASSES = "main-classes", TEST_CLASSES = "test-classes",
                        MAIN_SOURCES = "main-sources", TEST_SOURCES = "test-sources";
    static final String ELEMENTS = "archgateElements", MODULES = "archgateModules";
  }
  ```
  - **Every project with the plugin and `java`**:
    - creates a consumable configuration `archgateElements` (`canBeConsumed=true`, `canBeResolved=false`, attribute `Usage.USAGE_ATTRIBUTE = "archgate-elements"`);
    - with four outgoing variants, one per `KIND`;
    - artifacts are the class dirs (`builtBy` the compile tasks) and the source dirs.
  - **`modules(':x')`** adds `project(':x')` to the resolvable `archgateModules` configuration (same `Usage`).
  - **The task's dir inputs** = own dirs + `archgateModules.incoming.artifactView { attributes { attribute(KIND, k) } }.files` for each kind.
  - **`ownDirs`** = own main + test class dirs only.

- [ ] **Step 1: Write the failing tests.**
  - `multi-module` and `multi-module-violation` (expectations from Task 7).
  - `ArchgateElementsFunctionalTest.moduleWithoutPluginFailsWithHint`: `modules(':api')` where `:api` does not apply the plugin; failure output contains `"does not apply dev.quokkify.archgate"`, raised via a `doFirst` check on the resolved variants.
  - `ArchgateElementsFunctionalTest.siblingClassesAreBuiltFirst`: `:api:compileJava` appears in the executed tasks before `:architecture:archgateCheck`.
  - `ArchgateElementsFunctionalTest.withoutModulesOnlyOwnProjectIsChecked`.
- [ ] **Step 2: Run** `./gradlew :gradle-plugin:functionalTest --tests '*Elements*' --tests '*GradleFixture*'`. Expected: FAIL.
- [ ] **Step 3: Implement** `ArchgateElements` and the wiring above.
- [ ] **Step 4: Run** `./gradlew :gradle-plugin:build`. Expected: PASS, including the configuration-cache tests from Task 9 run on `multi-module` (add that parameter to `configurationCacheIsReused`).
- [ ] **Step 5: Commit** as `feat(gradle-plugin): check sibling modules through archgateElements`.

---

### Task 11: Maven ↔ Gradle parity test

**Files:**
- Create: `testing/parity/build.gradle` (not published; depends on `:testing:fixtures-support`, maven-verifier, `gradleTestKit()`, the plugin classpath of `:gradle-plugin` via its `pluginUnderTestMetadata`)
- Create: `testing/parity/src/test/java/dev/quokkify/archgate/parity/ReportParityTest.java`

**Interfaces:**
- Consumes: Task 8 report path `target/archgate/report.txt` (gate module); Task 9/10 report path `build/reports/archgate/report.txt`; `Fixture`.
- Produces: `static String normalize(String report)`:
  - replaces `\d+ ms` with `N ms` and `\d+\.\d s` with `N ms`;
  - normalizes line endings to `\n`.

- [ ] **Step 1: Write the failing test.**
  - `ReportParityTest.sameReportOnMavenAndGradle`, parameterized over the scenarios that produce a report: `pass`, `fail-on-threshold`, `multi-module`, `multi-module-violation`. For each scenario: run Maven (Task 8 harness) and Gradle (Task 9 harness) on the same fixture; assert `normalize(maven).equals(normalize(gradle))`, and the same success or failure.
  - `ReportParityTest.sameUnableToVerifyMessage`, over `missing-services`, `unregistered-rule`, `incompatible-rule-library`, `missing-context-provider`: the `UnableToVerifyError` message line (the first line containing the expected substring) is identical in both logs after stripping the Maven `[ERROR] ` prefix.
- [ ] **Step 2: Run** `./gradlew :testing:parity:test`. Expected: FAIL until the harness wiring exists; then PASS.
- [ ] **Step 3: Implement** `normalize` and the two runners. Reuse `MavenProjectWriter`/`GradleProjectWriter`; there is no new fixture code.
- [ ] **Step 4: Run** `./gradlew build`. Expected: PASS.
- [ ] **Step 5: Commit** as `test: assert identical reports from the Maven and Gradle plugins`.

---

### Task 12: `context-javaparser` + `rules-javaparser`

**Files:**
- Create: `context-javaparser/build.gradle` (`api(project(':api'))`, `api(libs.javaparser.core)` 3.28.2)
- Create in `context-javaparser/src/main/java/dev/quokkify/archgate/context/javaparser/`: `JavaParserContext.java`, `JavaParserContextProvider.java`
- Create: a `META-INF/services/dev.quokkify.archgate.api.ContextProvider` resource
- Create: `rules-javaparser/build.gradle` (`api(project(':context-javaparser'))`); `rules-javaparser/src/main/java/dev/quokkify/archgate/rules/javaparser/NoWildcardImportsRule.java`
- Tests: `JavaParserContextTest`, `NoWildcardImportsRuleTest`, plus a `NoServicesFileShippedTest` copy. Source fixtures under `context-javaparser/src/test/resources/sources/{main,test}/com/example/...` and `rules-javaparser/src/test/resources/sources/...`.

**Interfaces:**
- Consumes: `ProjectLayout`, `ContextProvider` (Task 3); `SimpleProjectLayout` (Task 4, test only).
- Produces:
  ```java
  public interface JavaParserContext extends AnalysisContext {
    String rootPackage();
    List<CompilationUnit> compilationUnits(Scope scope);   // lazily parsed, once per scope; only units whose package is rootPackage or below
  }
  public final class NoWildcardImportsRule implements ArchitectureRule<JavaParserContext>
  // name "No wildcard imports", severity WARNING, scopes MAIN+TEST
  // contract: "Imports must name the type. Replace each 'import x.y.*' with the types it uses; static wildcard imports are allowed."
  // violation line: "<path relative to its source dir>:<line> import <name>.*"
  ```
  - The parser uses `ParserConfiguration.LanguageLevel.JAVA_21`.
  - A parse problem → `UnableToVerifyError("Cannot parse <path>: <first problem>")`.
  - No unit under `rootPackage` in the scope → `UnableToVerifyError("No Java source under %s was found in the %s source directories %s.")` (same wording style as the source `ClassDirsContext.java:119-121`).
  - `buildTimes()` keys: `javaparser main`, `javaparser test`.

- [ ] **Step 1: Write the failing tests.**
  - `JavaParserContextTest.parsesOnlyRootPackage`, `parsesEachScopeOnce`, `missingTestSourcesIsUnableToVerify` (Review Focus 3), `parseErrorIsUnableToVerify`, `providerTypeIsJavaParserContext`.
  - `NoWildcardImportsRuleTest.wildcardImportIsReported` (contains `"Wildcard.java:3 import java.util.*"`), `staticWildcardIsAllowed`, `explicitImportsPass`, `contextTypeIsJavaParser`, `severityAndScopes`.
- [ ] **Step 2: Run** `./gradlew :context-javaparser:test :rules-javaparser:test`. Expected: FAIL.
- [ ] **Step 3: Implement** the types above.
- [ ] **Step 4: Run** `./gradlew :context-javaparser:build :rules-javaparser:build`. Expected: PASS.
- [ ] **Step 5: Add a fixture.** `fixtures/javaparser-wildcard/` (single module, `testDependencies=archgate-rules-javaparser`, a services file with `NoWildcardImportsRule`, one wildcard import in `src/main`). Expected: failure, contains `No wildcard imports` and `import java.util.*`. Because Tasks 8/9 parameterize over `Fixture.scenarios()`, both plugins pick it up. If this task lands before Task 8/9, they pick it up when they land. Run `./gradlew build` and expect PASS.
- [ ] **Step 6: Commit** as `feat: add JavaParser context and NoWildcardImportsRule`.

---

### Task 13: `context-classgraph` + `rules-classgraph`

**Files:**
- Create: `context-classgraph/build.gradle` (`api(project(':api'))`, `api(libs.classgraph)`; pin the latest 4.8.x release on Maven Central at implementation time, recorded in the catalog)
- Create in `context-classgraph/src/main/java/dev/quokkify/archgate/context/classgraph/`: `ClassGraphContext.java`, `ClassGraphContextProvider.java`, plus a services resource
- Create: `rules-classgraph/build.gradle`; `rules-classgraph/src/main/java/dev/quokkify/archgate/rules/classgraph/ServiceFileConsistencyRule.java`
- Tests: `ClassGraphContextTest`, `ServiceFileConsistencyRuleTest`, `NoServicesFileShippedTest` copy; fixtures `archfixtures/classgraph/**` plus test resources with services files.

**Interfaces:**
- Produces:
  ```java
  public interface ClassGraphContext extends AnalysisContext, AutoCloseable {
    String rootPackage();
    ScanResult scan(Scope scope);   // once per scope; overrideClasspath(classDirs(scope)), enableClassInfo(), enableExternalClasses(),
                                    // acceptPackages(rootPackage), acceptPaths("META-INF/services")
    void close();                   // closes every ScanResult
  }
  public final class ServiceFileConsistencyRule implements ArchitectureRule<ClassGraphContext>
  // name "Service file consistency", severity ERROR, scopes MAIN+TEST
  // contract: "Every public concrete class under the root package that implements a service interface with a
  //            META-INF/services file in the same class directories must be listed in that file."
  // violation line: "META-INF/services/<service>: <implementation> is not listed"
  ```
  This rule is the inverse of the ported `ServiceRegistrationRule`, which checks that listed classes are valid. See spec gap 1 in the final report.
  - No `META-INF/services` file in any scope → `UnableToVerifyError` "No META-INF/services file was found in …".
  - `buildTimes()` keys: `classgraph main`, `classgraph test`.

- [ ] **Step 1: Write the failing tests.**
  - `ClassGraphContextTest.scanIsCreatedOncePerScope`, `closeClosesScanResults`, `resourcesUnderServicesAreVisible`.
  - `ServiceFileConsistencyRuleTest.unlistedImplementationIsReported` (contains `"archfixtures.classgraph.UnlistedGreeter is not listed"`), `listedImplementationPasses`, `abstractAndNonPublicImplementationsAreIgnored`, `interfaceOutsideRootPackageIsResolved` (the service interface lives in another package), `noServicesFileIsUnableToVerify`, `contextTypeIsClassGraph`.
- [ ] **Step 2: Run** `./gradlew :context-classgraph:test :rules-classgraph:test`. Expected: FAIL.
- [ ] **Step 3: Implement** the types above.
- [ ] **Step 4: Run** `./gradlew :context-classgraph:build :rules-classgraph:build`. Expected: PASS. Add `ContextResolverIntegrationTest` in `rules-classgraph` that runs `GateRunner` through `ContextResolver` and asserts the context is closed after `ContextResolver.close()`.
- [ ] **Step 5: Add a fixture.** `fixtures/classgraph-unlisted/` (failure, contains `Service file consistency` and `is not listed`). Run `./gradlew build` and expect PASS.
- [ ] **Step 6: Commit** as `feat: add ClassGraph context and ServiceFileConsistencyRule`.

---

### Task 14: Documentation port, ADRs, agent guide

**Files:**
- Port with renames from `$SOURCE/`:
  - `README.md`, `README.ru.md`;
  - `docs/configuration{,.ru}.md`, `docs/faq{,.ru}.md`;
  - `docs/rules/*.md` (14 pairs);
  - `docs/assets/archgate/**` (12 files, binary copy).
- Rewrite:
  - `docs/development{,.ru}.md` for Gradle tasks: `build`, `functionalTest`, `mavenIT`, `publishToItRepo`, the pre-push hook;
  - `docs/releasing{,.ru}.md` for Release Please and `publish.yml`.
- Create:
  - `docs/rules/no-wildcard-imports{,.ru}.md`, `docs/rules/service-file-consistency{,.ru}.md`;
  - `docs/adr/0001-opt-in-rule-registration.md`, `0002-api-version-manifest.md`, `0003-backend-neutral-api.md`, `0004-gradle-explicit-modules.md`, `0005-gradle-build-for-maven-plugin.md` (records the Task 1 outcome).
- Port: `$SOURCE/AGENTS.md`, its invariants table, rewritten for the new modules.

**Content rules:**
- **README:**
  - badges: Java 21, Gradle, Maven 3.9+, ArchUnit, License Apache-2.0; no release badge until Task 16;
  - a "Use with Maven" section (source README `pom.xml` snippet with the new coordinates) and a "Use with Gradle" section (spec §8 snippet);
  - the built-in rules table gains a `Backend` column and the two new rules.
- **ADRs** use the headings Context / Decision / Consequences. They contain no ticket keys or merge-request numbers. Content comes from the source `docs/superpowers/specs/*.md` and from spec §§2–8.
- Every Maven coordinate is `dev.quokkify.archgate:*`. Every services path is `META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`.

- [ ] **Step 1: Write the failing check.** `docs/DocsLinkTest` lives in `testing/fixtures-support`'s test source set, so it runs in `check`. It asserts:
  - every relative link in `README*.md`, `docs/**/*.md` resolves to an existing file;
  - every class named in a README rules table exists under `rules-*/src/main/java`;
  - `README.md` and `README.ru.md` list the same rule names.
- [ ] **Step 2: Run** `./gradlew :testing:fixtures-support:test --tests '*DocsLinkTest'`. Expected: FAIL (docs absent).
- [ ] **Step 3: Port and write** the docs per the content rules.
- [ ] **Step 4: Run** `./gradlew build`. Expected: PASS. Run `git grep -niEf .git/info/archgate-denylist`. Expected: no output.
- [ ] **Step 5: Commit** as `docs: port user docs, add ADRs and agent guide`.

---

### Task 15: `bom`, publishing metadata, CI from `quokkify/ci-kit`

**Files:**
- Create: `bom/build.gradle` (`java-platform`, `archgate.publish-conventions`, `constraints { api(project(':api')) … }` for all ten other published modules)
- Create via Copier:
  - `.copier-answers.yml`, `.github/workflows/{validate,release,codeql,gitleaks,copier-update}.yml`;
  - `.github/release-please/{config,manifest}.json`, `.github/scripts/enrich_release_notes.py`;
  - `.github/renovate.json`, `.github/pull_request_template.md`.
- Create: `.github/workflows/publish.yml` (project-owned)
- Modify: `.github/release-please/manifest.json` → `{ ".": "0.0.0" }`; `version.txt` stays `0.0.0`.

- [ ] **Step 1: Write the failing test.** `bom/src/test` is not possible for `java-platform`, so add `BomCompletenessTest` to `testing/fixtures-support`. It parses `bom/build/publications/maven/pom-default.xml` and asserts `dependencyManagement` lists exactly the ten artifact ids from Global Constraints other than `archgate-bom`.
- [ ] **Step 2: Run** `./gradlew :bom:generatePomFileForMavenPublication :testing:fixtures-support:test --tests '*Bom*'`. Expected: FAIL. Then implement `bom/build.gradle` and run again. Expected: PASS.
- [ ] **Step 3: Generate CI.** Run `copier copy --vcs-ref v3.3.0 gh:quokkify/ci-kit .` with answers:
  - `module=legacy`, `project_name=archgate`, `toolkit_version=v3.3.0`;
  - `components=[{type: java, path: .}]`, `docker=false`;
  - `codeql=true`, `codeql_languages=[java-kotlin, actions]`;
  - `gitleaks=true`, `allure_report=false`;
  - `release_please=true`, `release_mode=single`;
  - `renovate=true`, `renovate_presets=[default, java, github-actions]`.

  The generated `validate.yml` calls `quokkify/ci-kit/.github/workflows/java-ci.yml@v3.3.0`. Its default Gradle commands are `check -x test`, `test` and `build`; `functionalTest` and `mavenIT` run because they are wired into `check`.
- [ ] **Step 4: Write `publish.yml`.**
  - Triggers:
    - `workflow_run: { workflows: [Release Please], types: [completed] }` — a release created by Release Please with `GITHUB_TOKEN` does not trigger `release` events (spec gap 2);
    - `workflow_dispatch: { inputs: { tag: { required: true } } }`.
  - The job runs only if the run concluded `success` (or on dispatch).
  - Steps:
    1. check out the `workflow_run.head_sha` (or the tag) with `fetch-depth: 0`;
    2. `tag=$(git tag --points-at HEAD | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -1)`; exit 0 when empty;
    3. assert `"v$(cat version.txt)" = "$tag"`;
    4. `actions/setup-java` temurin 21, `gradle/actions/setup-gradle`;
    5. `./gradlew build publishToMavenCentral publishPlugins -Prelease=true`.
  - Env from secrets:
    - `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`;
    - `ORG_GRADLE_PROJECT_signingInMemoryKey`, `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`;
    - `GRADLE_PUBLISH_KEY`, `GRADLE_PUBLISH_SECRET`.
  - Permissions: `contents: read`.
  - Pin actions by SHA, as the generated workflows do.
- [ ] **Step 5: Verify locally.**
  - `./gradlew build publishToItRepo -Prelease=true`. Expected: PASS, and `build/it-repo/dev/quokkify/archgate/` contains 11 artifact directories plus the plugin marker `dev/quokkify/archgate/dev.quokkify.archgate.gradle.plugin`.
  - `unzip -p` of any jar in it shows `Archgate-Api-Version: 0.0.0`.
  - `actionlint .github/workflows/*.yml`. Expected: no findings.
- [ ] **Step 6: Commit** as `ci: add bom, ci-kit workflows, Release Please and publishing`. Open a PR; the generated `Validate` workflow must be green on it.

---

### Task 16: Release 0.1.0 (owner actions + checklist)

**Files:** none, except Step 4's empty commit.

- [ ] **Step 1: Owner, one time.**
  - Verify the namespace `dev.quokkify` in Central Portal (DNS TXT on `quokkify.dev`).
  - Create a Central Portal user token.
  - Create a GPG key and publish it to `keys.openpgp.org`.
  - Create a Gradle Plugin Portal API key.
  - Add the six repository secrets from Task 15 Step 4.
  - Enable "Allow GitHub Actions to create and approve pull requests" for Release Please.
- [ ] **Step 2: Gate.** Check all of these:
  - `main` CI is green;
  - `git grep -niEf .git/info/archgate-denylist $(git rev-list --all)` returns nothing;
  - `./gradlew build` is green locally.
- [ ] **Step 3: Owner confirms the release.** Publishing to Maven Central is irreversible.
- [ ] **Step 4: Trigger the first release.** Push an empty commit through a PR with message `chore: release 0.1.0` and footer `Release-As: 0.1.0`. Merge it, then merge the Release Please PR it opens.
- [ ] **Step 5: Verify.**
  - The `publish` workflow is green.
  - In Central Portal the deployment for `dev.quokkify.archgate` 0.1.0 is `VALIDATED`. The owner presses Publish (`automaticRelease = false`).
  - `https://plugins.gradle.org/plugin/dev.quokkify.archgate` shows 0.1.0.
- [ ] **Step 6: Smoke test.** Run fixture `pass` against the public 0.1.0 artifacts with both Maven and Gradle (the Task 7 writers with `itRepo` = Central). Expected: `Gate PASSED: 2 rules, no findings` in both.
- [ ] **Step 7: Add the release badge** to the README (PR `docs: add release badge`).

---

## Acceptance criteria → spec §1

| Spec success criterion | Proven by |
|---|---|
| 1. Same findings, report and result on Maven and Gradle | Task 11 `ReportParityTest` |
| 2. 14 rules unchanged on ArchUnit | Task 6 ported rule tests, assertions unchanged |
| 3. One example rule per extra backend passes/fails | Task 12 `NoWildcardImportsRuleTest` + `javaparser-wildcard`; Task 13 `ServiceFileConsistencyRuleTest` + `classgraph-unlisted` |
| 4. Up-to-date, build cache, configuration cache ×2 | Task 9 `CachingFunctionalTest`; Task 10 multi-module cache case |
| 5. `v0.1.0` on Maven Central and Plugin Portal from CI | Task 16 Step 5 |
| 6. No former-host references | Task 0/2 denylist hook; Task 14 Step 4; Task 16 Step 2 |

## Risks

| Risk | Mitigation |
|---|---|
| gradlex plugin fails on Gradle 9.7.1 or with the configuration cache | Task 1 decides before Task 8; the fallback is fully specified |
| Worker `classLoaderIsolation` leaks Gradle-bundled classes into the rule loader | `GateLauncher` builds its own `URLClassLoader` with the plugin loader as parent, exactly as the Maven path; Review Focus 1 test |
| maven-verifier 2.0.0 API differs from the names used here | Task 8 Step 2 is the first place it compiles; adjust to the 2.0.0 API without changing assertions |
| A release PR made with `GITHUB_TOKEN` does not trigger CI checks | Owner setting in Task 16 Step 1; maintainers can re-run `Validate` on the release PR by hand |
| JaCoCo 0.80 floor blocks the ported plugin modules | The floor applies to `api`, `core`, `context-*`, `rules-*`; plugin modules are covered by functional tests and excluded from the floor |

## Self-review notes

- **Spec coverage:** spec §§3–13 map to Tasks 2–16. §12 hygiene → Tasks 0, 2, 14, 16. §13 risks → Task 1, Review Focus 1, the Risks table.
- **Type names are consistent across tasks:** `ArchitectureRule<C>`, `ContextResolver.get`, `LaunchRequest`, `ModuleDirs`, `ReportSink`, `GateFailure`, `ArchUnitContext.main/test/all/layout`.
- **Deliberate refinements of the spec, flagged for the owner:**
  - `AnalysisContext.buildTimes()` default;
  - `ArchUnitContext.layout()`;
  - the `NEVER` value of `failOn`;
  - `ServiceFileConsistencyRule` semantics;
  - publish via `workflow_run`;
  - `version.txt` semantics.
