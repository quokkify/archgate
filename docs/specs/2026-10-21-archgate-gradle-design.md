# ArchGate on Gradle: two build plugins and pluggable analysis contexts

Date: 2026-10-21
Status: approved design; implemented in 0.1.0

## 1. Goal

Port ArchGate — architecture rules as a build gate — into `quokkify/archgate` as an
open-source project that:

- builds entirely with Gradle (no Maven build of the repository itself);
- ships a Maven plugin and a Gradle plugin that run the same engine and behave the same;
- keeps the analysis backend out of the API: ArchUnit, JavaParser and ClassGraph each live
  in their own module, so a backend and its rules can be adopted or copied without the others.

The source of the port is an existing, working Maven implementation (modules `api`, `core`,
`rules` with 14 rules, `maven-plugin`, `bom`). Transfer of that code is authorized by its owner.

### Success criteria

1. A Maven consumer and a Gradle consumer with the same sources, the same rules and the same
   `META-INF/services` file get the same findings, the same report text and the same pass/fail
   result.
2. All 14 existing rules run on the ArchUnit backend with behaviour unchanged.
3. One example rule each on the JavaParser and ClassGraph backends passes and fails as specified.
4. The Gradle task is up-to-date on a second run, reuses the build cache, and passes twice in a row
   with `--configuration-cache`.
5. `v0.1.0` is published to Maven Central (all artifacts) and the Gradle Plugin Portal from a
   GitHub Actions tag build.
6. The repository contains no references to the former host: its name, internal hosts, registries
   or ticket keys.

### Out of scope for 0.1.0

- Taikai backend.
- Rules from consumer projects (test-case link, Jackson tree usage); they come in a later release.
- Automatic discovery of sibling modules' test classes in Gradle (explicit `modules(...)` only).

## 2. Decisions

| Topic | Decision |
|---|---|
| License | Apache-2.0 |
| Coordinates | groupId `dev.quokkify.archgate`; Gradle plugin id `dev.quokkify.archgate` |
| Package | `dev.quokkify.archgate.*` (replaces the original package) |
| Toolchain | Java 21, Gradle 9.7 wrapper |
| Maven plugin build | `org.gradlex.maven-plugin-development`; fallback: a hand-written `plugin.xml` guarded by a descriptor test |
| Branching | trunk on `main` via pull requests; Release Please tags `vX.Y.Z` |
| First version | `0.1.0`, forced with a `Release-As: 0.1.0` commit. `version.txt` holds the last released version (`0.0.0` before the first release); non-release builds append `-SNAPSHOT` |
| Repository history | new `main` starts from an orphan commit; the earlier migration branch is discarded |

## 3. Modules

```
archgate/
├── api                 contract only, no analysis library
├── core                engine, build-tool agnostic
├── context-archunit    ArchUnit backend
├── context-javaparser  JavaParser backend
├── context-classgraph  ClassGraph backend
├── rules-archunit      14 rules ported from the original
├── rules-javaparser    NoWildcardImportsRule
├── rules-classgraph    ServiceFileConsistencyRule
├── maven-plugin        Maven adapter
├── gradle-plugin       Gradle adapter
└── bom                 java-platform with every published artifact
```

Dependency directions:

```
rules-X   → api, context-X
context-X → api, <library X>
core      → api
plugins   → core
```

Nothing depends on `rules-*`, and no `rules-*` module ships a `META-INF/services` file. A rule runs
only when the consumer lists it.

## 4. API (`api`)

```java
public interface ArchitectureRule<C extends AnalysisContext> {
    String name();
    Severity severity();
    Set<Scope> scopes();
    Class<C> contextType();
    void verify(C context);
}

public interface AnalysisContext {
    default Map<String, Duration> buildTimes() { return Map.of(); }
}

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

- `Severity { INFO, WARNING, ERROR }`, `Scope { MAIN, TEST }` — unchanged from the original.
- A finding is an `ArchitectureViolation` (or `AssertionError`) thrown from `verify`.
- `UnableToVerifyError` means the check could not run. It always fails the build, whatever the
  threshold.
- Context instances that hold resources implement `AutoCloseable`; core closes them after the run.
- `buildTimes()` reports how long each model took to build, keyed by model name. The report prints
  these timings as the original did.
- The `api` jar manifest carries `Archgate-Api-Version`. Rule jars carry the API version they were
  built against. Release builds stamp the release version; every `-SNAPSHOT` build stamps
  `dev-SNAPSHOT`, the only non-`X.Y.Z` value the compatibility check accepts.

## 5. Analysis contexts

| Module | Context | Content | Built from |
|---|---|---|---|
| `context-archunit` | `ArchUnitContext` | `main()`, `test()`, `all()` → `JavaClasses` filtered by `rootPackage..`; `layout()` → `ProjectLayout` (needed by `ServiceRegistrationRule`); helpers `ArchRuleChecks`, `GeneratedCode` | `classDirs` |
| `context-javaparser` | `JavaParserContext` | `compilationUnits(Scope)` → `List<CompilationUnit>` | `sourceDirs` |
| `context-classgraph` | `ClassGraphContext` (`AutoCloseable`) | `scan(Scope)` → `ScanResult` | `classDirs` + resources |

Each context module registers its `ContextProvider` through its own `META-INF/services` file. This
is the only services file a published module may contain.

An empty model for a scope a rule requests raises `UnableToVerifyError`. This is the original
`ClassDirsContext` behaviour, applied to every backend.

Example rules:

- `NoWildcardImportsRule` (JavaParser) reports `import x.*;` declarations, which exist only in the
  sources.
- `ServiceFileConsistencyRule` (ClassGraph) reports public concrete classes under `rootPackage` that
  implement an interface the module has a `META-INF/services` file for but are not listed in that
  file. This complements `ServiceRegistrationRule`, which checks the opposite direction (entries
  in services files that point to classes that do not exist or do not implement the interface).

## 6. Engine (`core`)

- **`RuleRegistry`**
  - Loads `ArchitectureRule` through `ServiceLoader` and dedupes by type.
  - Before instantiating a rule (before `Provider::get`), checks `Archgate-Api-Version` of the jar
    that defines the rule and of the jars of its superclasses. A rule library newer than the runtime
    API fails fast.
  - An empty registry is an error.
- **`RegistrationCheck`** fails when a public concrete rule class in the gate module's own class
  dirs is not listed in its services file. It uses plain reflection over the class files, so `core`
  depends on no analysis library, and the consumer's ArchUnit version never conflicts with the
  plugin's.
- **`FailOn`** accepts `INFO | WARNING | ERROR | NEVER`, default `WARNING`. `NEVER` reports but
  never fails on findings; `UnableToVerifyError` still fails the build.
- **`ContextResolver`**
  - Maps `contextType()` to a provider found through `ServiceLoader`.
  - Creates each context lazily, at most once per run, and closes it after the run.
  - A missing provider raises `UnableToVerifyError` that names the `context-*` artifact to add.
- **`GateRunner`** runs one virtual thread per rule, collects every finding and sorts them by rule
  name.
- **`GateLauncher`**
  - Input is a `LaunchRequest` with these fields:
    - `rootPackage`, `failOn`, `skip`;
    - `layout`: class and source dirs per scope, across the selected modules;
    - `ownDirs`: the gate module's own class dirs;
    - `ruleClasspath`;
    - `sink: Consumer<String>`;
    - an optional `reportFile`.
  - Builds `URLClassLoader(ruleClasspath, parent = plugin class loader)`, so the plugin's `api` and
    `core` take precedence, and swaps the thread context class loader for the run.
  - Returns a `GateResult` with the findings and the worst severity. It throws `GateFailure` when
    the threshold is reached and propagates `UnableToVerifyError`.
- **`ReportFormatter`** produces the same text for both adapters.

## 7. Maven plugin

- Goal `check`, default phase `verify`, `requiresDependencyResolution = TEST`. It also runs with
  `-DskipTests`.
- Parameters:
  - `rootPackage` (required);
  - `failOn` (default `WARNING`, property `archgate.failOn`);
  - `skip` (property `archgate.skip`);
  - `modules` (optional).
- Module selection:
  - Default: the current module plus the reactor modules it depends on.
  - `modules` replaces the default.
  - A selected module that has not been compiled fails the build; the gate module itself is exempt.
  - The original `ModuleSelection` and `MainDir` logic is kept.
- `sourceDirs` come from `compileSourceRoots` and `testCompileSourceRoots` of the selected modules.
- `ruleClasspath` is the test classpath elements of the gate module.
- Report lines are logged at the level of the worst severity.
- Errors:
  - `GateFailure` → `MojoFailureException`;
  - `UnableToVerifyError` → `MojoExecutionException`.
- `maven-core` and `maven-plugin-api` are `compileOnly`. The minimum Maven version (3.9) is declared
  in the descriptor.
- The publication has `packaging = maven-plugin` and includes `plugin.xml` and `plugin-help.xml`.

## 8. Gradle plugin

```groovy
plugins { id 'java'; id 'dev.quokkify.archgate' }

archgate {
    rootPackage = 'com.acme'
    failOn = 'WARNING'            // or -Parchgate.failOn=ERROR
    skip = false                  // or -Parchgate.skip=true
    modules(':api', ':domain')    // optional
}

dependencies {
    testImplementation platform('dev.quokkify.archgate:archgate-bom:0.1.0')
    testImplementation 'dev.quokkify.archgate:archgate-rules-archunit'
}
```

- Requires the `java` plugin. It registers the task `archgateCheck` (group `verification`), which
  depends on `testClasses` and is wired into `check`.
- Task inputs:
  - `@Classpath` — `testRuntimeClasspath`;
  - `@InputFiles @PathSensitive(RELATIVE)` — class and source dirs of the selected modules;
  - `@Input` — `rootPackage`, `failOn`.

  Output: `@OutputFile build/reports/archgate/report.txt`. The task is cacheable.
- Configuration-cache safe: no `Project` access at execution time; everything is passed through
  `Provider`/`FileCollection`.
- Execution goes through the Worker API with `classLoaderIsolation`. The worker classpath is the
  plugin's `core` + `api`, followed by `testRuntimeClasspath`. Switching to `processIsolation` is
  the documented escape hatch if ArchUnit version clashes appear.
- Multi-module:
  - Every project that applies the plugin exposes a consumable configuration `archgateElements`
    with its main and test class dirs and source dirs.
  - `modules(':x')` adds a project dependency on `:x`'s `archgateElements`.
  - Without `modules`, only the current project is checked. This differs from Maven on purpose:
    Gradle has no cheap, correct way to reach sibling test classes.
  - Every module named in `modules(...)` must apply the plugin itself. Otherwise the task fails
    with a message that contains `does not apply dev.quokkify.archgate`.
  - Modules that only expose their classes to a gate set `archgate { skip = true }`. On a project
    that is not skipped, an unset `rootPackage` fails the build.
- Supported consumer Gradle version for 0.1.0: 9.7.1 and newer. Older versions are not tested.
- Errors: `GateFailure` and `UnableToVerifyError` become `GradleException`. The message contains
  the report, or the "not verified" reason.

## 9. Consumer contract (shared)

- The consumer lists rules in `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`.
- `failOn` threshold semantics, report format, error texts and exit behaviour are identical in both
  plugins. Section 10 includes tests that check this.

## 10. Testing

- **Unit (JUnit 5)**
  - Ported tests of `core` and of the rules, using real rule jars (`RuleJars`) and the
    `archfixtures.*` packages.
  - New tests for `ContextResolver`:
    - a missing provider produces a hint;
    - a context is created lazily and once;
    - `AutoCloseable` contexts are closed.
  - Per-backend tests for each context and each example rule.
- **Shared fixtures** live in `fixtures/<scenario>/` (sources and the services file only):
  - pass;
  - fail-on-threshold;
  - skip;
  - missing-services;
  - unregistered-rule;
  - multi-module;
  - multi-module-violation;
  - incompatible-rule-library;
  - missing-context-provider.
- **Gradle plugin:** a `functionalTest` source set using TestKit with `withPluginClasspath()`. Each
  fixture gets a generated `build.gradle`/`settings.gradle`. Gradle-only cases:
  - the second run is `UP-TO-DATE`;
  - two consecutive `--configuration-cache` runs succeed;
  - the build cache gives a `FROM-CACHE` hit.
- **Maven plugin:** a `mavenIT` source set using `maven-verifier`.
  - The Maven 3.9 distribution is resolved as `org.apache.maven:apache-maven:3.9.x:bin@zip` and
    unpacked by a Gradle task.
  - The artifacts are published to `build/it-repo` before the tests.
  - Each fixture gets a generated `pom.xml`. Assertions from the former `verify.groovy` scripts
    become assertions on `build.log`.
- **Parity test:** for each fixture, the report text produced by the Maven and the Gradle runs is
  the same.
- **Static checks in `check`:** Spotless (palantir-java-format), a project-owned Checkstyle config,
  JaCoCo.

## 11. Build, CI, release

- `settings.gradle` includes all modules. Dependencies are declared only in `gradle/libs.versions.toml`,
  and the catalog contains only what the project uses.
- Convention plugins in `build-logic/` handle:
  - Java library defaults;
  - the publishing setup (`com.vanniktech.maven.publish`, Central Portal, signing, POM metadata,
    Apache-2.0);
  - writing the `Archgate-Api-Version` manifest attribute.
- CI is generated from the org template `quokkify/ci-kit` v3.3.0 (Copier, module `legacy`, the only
  module that offers Release Please). The generated workflows call its reusable workflows pinned to
  that tag:
  - **CI:** `validate.yml` calls `java-ci.yml` on pull requests and pushes to `main` (JDK 21,
    Gradle). `functionalTest` and `mavenIT` are wired into `check`, so the default Gradle commands
    run them.
  - **Releases:** Release Please in single-package mode. Its manifest and `version.txt` hold the
    last released version. Merging the release PR creates the tag `vX.Y.Z` and the GitHub Release.
  - **Publishing:** a project-local `publish.yml` triggered by `workflow_run` of the Release Please
    workflow. A release created with `GITHUB_TOKEN` does not fire `release` events, so
    `release: published` cannot be used. The job checks out the release tag, asserts
    `v$(cat version.txt)` equals the tag, then runs:
    1. `publishToMavenCentral` (all artifacts, signed);
    2. `publishPlugins` to the Gradle Plugin Portal.
- Renovate uses the toolkit presets and the Gradle manager.
- Every change, this spec included, lands on `main` through a pull request. The one exception is
  the bootstrap: a single orphan commit with `LICENSE` and `README.md` that creates the new `main`.
- One-time owner actions:
  - verify the `dev.quokkify` namespace in Central Portal (DNS TXT);
  - create a Plugin Portal API key;
  - add repository secrets: GPG key and passphrase, Central token, Plugin Portal key and secret.

## 12. Migration hygiene

- **Port:**
  - code and tests of `api`, `core`, `rules` (→ `rules-archunit`, plus the extraction into
    `context-archunit`) and `maven-plugin`;
  - `docs/` (EN and RU), README files, `docs/rules/*`, brand assets.
- **Rename:**
  - the package and every string constant that contains it, including the services file name and
    `RuleRegistry` hints;
  - Maven coordinates in the documentation.
- **Do not port:**
  - `.gitlab*`, `.gitlab-ci.yml`, `.mvn/`, `mvnw*`, `pom.xml` files;
  - CI `settings.xml`, `renovate.json` with internal presets;
  - internal repository-manager and registry URLs, release badges;
  - `.configs/code_style` (downloaded from an internal host), `.superpowers/`, `.omc/`,
    `.agents/skills/release-publisher`;
  - ticket and merge-request numbers. Former design specs are rewritten as ADRs in `docs/adr/`.
- **Pre-push gate:** a local, uncommitted denylist of former-host names, hosts and ticket keys is
  grepped over the tree and the history before each push; it must return nothing.
- **Repository:**
  - `main` is recreated from an orphan history and becomes the default branch;
  - `feat/archgate-plugin-migration` is deleted after a separate owner confirmation.

## 13. Risks

| Risk | Mitigation |
|---|---|
| `org.gradlex.maven-plugin-development` has no stated Gradle 9 support | First task is a spike; fallback is a hand-written `plugin.xml` plus the existing descriptor test |
| ArchUnit version on the consumer classpath differs from the plugin's | Plugin-first class loading as in the original; Gradle `processIsolation` escape hatch |
| A neutral API changes the rule signature (`ArchitectureRule<C>`) | The change is mechanical for the 14 rules; the API version starts fresh under the new group |
| Gradle multi-module semantics differ from Maven | Documented explicitly; the parity test covers single-module and explicit `modules` cases |
