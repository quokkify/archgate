# AGENTS.md

Working notes for AI agents and new contributors in **archgate** (`quokkify/archgate`).

## What this repository is

ArchGate runs architecture rules as a build gate for Maven and Gradle. This repository *produces* the gate: a backend-neutral API, a build-tool-agnostic engine, three analysis backends (ArchUnit, JavaParser, ClassGraph) with their built-in rules, a Maven plugin, a Gradle plugin and a BOM. It publishes to Maven Central and the Gradle Plugin Portal. There is no application code here: consumers add a plugin to their own build and list rules in their own `META-INF/services` file.

Docs are bilingual: every `docs/X.md` has `docs/X.ru.md`, and `README.md` has `README.ru.md`. English is the source; a change to one side requires the other in the same commit. ADRs in `docs/adr/`, `docs/ci-kit.md` (owned by the ci-kit toolkit) and the specs in `docs/specs/` are English only.

The repository is public. Nothing from a private employer, internal host, registry, ticket tracker or machine path may appear in code, docs, assets, commit messages or history.

## Module map

| Module | Package | Holds | Hard limits |
|---|---|---|---|
| `api` | `dev.quokkify.archgate.api` | Contract: `ArchitectureRule<C>`, `AnalysisContext`, `ContextProvider<C>`, `ProjectLayout`, `Severity`, `Scope`, `ArchitectureViolation`, `UnableToVerifyError` | No analysis library, no build-tool types, no logging |
| `core` | `dev.quokkify.archgate.core`, `.core.launch` | `RuleRegistry`, `RegistrationCheck`, `SimpleProjectLayout`, `ApiCompatibilityCheck` (with `ApiVersion`, `ApiVersionManifest`), `ContextResolver`, `GateRunner` (one virtual thread per rule), `FailOn`, `GateResult`, `RuleOutcome`, `ReportFormatter`, `RootPackage`, `GateLauncher`, `LaunchRequest`, `GateFailure`, `ModuleDirs`, `ReportSink` | Build-tool agnostic and analysis-library free. Depends on `api` only |
| `context-archunit` | `dev.quokkify.archgate.context.archunit` | `ArchUnitContext` (`main()`, `test()`, `all()`, `layout()`), `ArchUnitContexts`, `DirectoryArchUnitContext`, `ArchUnitContextProvider`; helpers `ArchRuleChecks`, `GeneratedCode` in `.support` | Depends on `api` and ArchUnit |
| `context-javaparser` | `dev.quokkify.archgate.context.javaparser` | `JavaParserContext` (`compilationUnits(Scope)`, `relativePath`), `DirectoryJavaParserContext`, `JavaParserContextProvider` | Depends on `api` and JavaParser; built from source directories |
| `context-classgraph` | `dev.quokkify.archgate.context.classgraph` | `ClassGraphContext` (`scan(Scope)`, `layout()`; `AutoCloseable`), `DirectoryClassGraphContext`, `ClassGraphContextProvider` | Depends on `api` and ClassGraph |
| `rules-archunit` | `dev.quokkify.archgate.rules.archunit` | The 14 ArchUnit rules | Ships **no** `META-INF/services` rule file. Every rule has a public no-arg constructor with defaults; parameterised ones add a `protected` constructor |
| `rules-javaparser` | `dev.quokkify.archgate.rules.javaparser` | `NoWildcardImportsRule` | Same |
| `rules-classgraph` | `dev.quokkify.archgate.rules.classgraph` | `ServiceFileConsistencyRule` | Same |
| `maven-plugin` | `dev.quokkify.archgate.maven` | Goal `check` in `verify` (`CheckMojo`), `ModuleSelection`, `MainDir` | Thin adapter over core. Built with `org.gradlex.maven-plugin-development` |
| `gradle-plugin` | `dev.quokkify.archgate.gradle` | Plugin id `dev.quokkify.archgate`, task `archgateCheck` (`ArchgateCheckTask`), `ArchgatePlugin`, `ArchgateExtension`, `ArchgateElements`, `ExistingDirectories`, `FailureMessage`, `GateWorkParameters`, `GateWorkAction` | Thin adapter over core; configuration-cache safe |
| `bom` | — | `java-platform` over every published artifact | No sources |
| `testing/fixtures-support` | `dev.quokkify.archgate.testing` | Fixture model, generators of `build.gradle` and `pom.xml` for a fixture, runners `MavenRun` (with `Outcome`) and `GradleRun`, `DocsLinkTest`, `BomCompletenessTest` | Test support only, not published |
| `testing/parity` | — | Runs every fixture except `skip` under Maven and Gradle and compares the reports or, for `unableToVerify` runs, the message | Test only, not published |
| `build-logic` | — | Convention plugins `archgate.java-conventions`, `archgate.publish-conventions`, `archgate.rules-conventions` (applied by every `rules-*` module: `test` runs after `jar` and receives the jar's path as the `archgate.rulesJar` system property, read by `NoServicesFileShippedTest`), `archgate.it-repo`, `ArchgateVersions`, `FixtureTests` (wires `mavenIT`, `functionalTest` and parity) | |

Dependency direction: `rules-X → api, context-X`; `context-X → api, library X`; `core → api`; `plugins → core`. **Nothing depends on a `rules-*` module** (tests excepted). Test source sets of `context-*` and `rules-*` may depend on `core`.

`fixtures/<scenario>/` holds shared scenarios (sources, services file, `fixture.properties`, `expected.properties`) used by `functionalTest`, `mavenIT` and the parity test.

`.ci/` and `.copier-answers*.yml` belong to the ci-kit toolkit; change answers with `copier update --data <question>=<value>` (see `docs/ci-kit.md`) and toolkit files in ci-kit, not by hand. `docs/specs/` holds approved designs.

## Contracts you must not break

1. **Opt-in registration.** A rule runs only if the consumer lists it in `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`. No `rules-*` module ships that file, ever. The only services files a published module may contain are the `ContextProvider` registrations of the `context-*` modules. `RuleRegistry` and `RegistrationCheck` enforce this, including nested `public static` rule classes (binary name `Outer$Inner`).
2. **Fail closed.** `UnableToVerifyError extends Error` always fails the build, whatever `failOn` is: an empty or missing class view, an unbuilt selected module, a missing context provider, a registration problem, or a rule throwing anything that is not a finding. Never turn an unable-to-verify into a pass, and never swallow it.
3. **Two ways to report a finding.** `ArchitectureViolation` (usually via `checkViolations`) or an `AssertionError`. Nothing else.
4. **`api` and `core` stay light.** `api` has no analysis library. `core` has no analysis library and no build-tool types (`RegistrationCheck` uses plain reflection). No logging framework anywhere in `api`, `core`, `context-*` or `rules-*`; only the plugins log, through their build tool.
5. **Backends stay separate.** A rule depends on `api` and its own `context-*` module. A context module is the only place that touches its analysis library on behalf of rules.
6. **Maven floor 3.9.0.** `maven-core` and `maven-plugin-api` are pinned to 3.9.0 `compileOnly` on purpose. The generator writes no `<requiredMavenVersion>`, so `maven-plugin/build.gradle` patches the descriptor and `PluginDescriptorTest` fails if the minimum disappears or moves. Raise the floor only deliberately ([ADR 0005](docs/adr/0005-gradle-build-for-maven-plugin.md)).
7. **Gradle 9.7.1+.** The Gradle plugin is configuration-cache safe: no `Project` access at execution time, everything flows through `Provider` and `FileCollection`. The task stays cacheable with declared inputs and one output file.
8. **No version is hand-bumped.** `version.txt` holds the last released version (`0.0.0` before the first release); normal builds append `-SNAPSHOT`, and `-Prelease=true` uses it as is. Release Please owns `version.txt` and the changelog. Never commit a "bump version" change.
9. **The report text is an output contract.** Maven and Gradle must print the same text for the same fixture; the parity test and `expected.properties` assert substrings. Change `ReportFormatter` or an error message only together with the affected expectations.
10. **Messages are project-neutral and actionable.** No consumer-specific names in code or rule text. Say what to do next (for example which line to add to the services file, which `archgate-context-*` artifact to add).
11. **Rules are configured by subclassing, never by build-file settings.** Parameters go into the `protected` constructor, severity via `severity()`. There is no per-rule configuration in a build file.
12. **Class and source directories come from the build tool** (Maven reactor, Gradle task inputs), never from relative paths or convention guessing. Gradle multi-module is explicit: `modules(...)`, every module applies the plugin ([ADR 0004](docs/adr/0004-gradle-explicit-modules.md)).
13. **Rule jars declare the API they need.** `api` and every `rules-*` jar stamp `Archgate-Api-Version` (the release version, or `dev-SNAPSHOT` for any `-SNAPSHOT` build) from `archgate.java-conventions`. `RuleRegistry` runs `ApiCompatibilityCheck` before instantiating any rule. Never drop the stamp ([ADR 0002](docs/adr/0002-api-version-manifest.md)).
14. **Public repository hygiene.** The local pre-push hook greps the tree and the pushed commits against a private denylist. Never commit the denylist, and never name what it holds in a file, commit message or PR text.

## Where a change goes

| Change | Touch |
|---|---|
| New built-in rule | `rules-<backend>/src/main/java/.../XRule.java` + fixtures under `rules-<backend>/src/test/java/archfixtures/<area>/` + `XRuleTest` + `docs/rules/x-rule.md` and `.ru.md` + a row in the README rule table (both languages) |
| New analysis backend | `context-<name>` and `rules-<name>` modules (the latter applies `archgate.rules-conventions`), a `ContextProvider` services file in the context module, `settings.gradle`, the `bom`, README tables (both languages), an ADR if the contract changes |
| New plugin parameter | `CheckMojo` `@Parameter` and `ArchgateExtension`/`ArchgatePlugin`, a row in `docs/configuration.md` and `.ru.md`, a fixture for the behaviour, `PluginDescriptorTest` if the minimum Maven or descriptor changes |
| Behaviour of the API or helpers | `api` or `context-*`; grep all callers (`rules-*`, plugin tests, README snippets) before changing signatures |
| New end-to-end scenario | `fixtures/<name>/`; picked up by `functionalTest`, `mavenIT` and parity |
| Release or CI logic | `.github/workflows/*`, `docs/releasing.md` and `.ru.md` |
| Dependency versions | `gradle/libs.versions.toml` only; the catalog contains only what the project uses |

## Build and verify

JDK 21 is required (toolchain and `--release 21`). Always use `./gradlew`.

```sh
./gradlew build                                   # everything: unit tests, functionalTest, mavenIT, parity, Spotless, Checkstyle, JaCoCo, docs checks
./gradlew spotlessApply                           # fix formatting
./gradlew :api:test                               # one module
./gradlew :gradle-plugin:functionalTest           # Gradle plugin through TestKit
./gradlew :maven-plugin:mavenIT                   # Maven plugin through maven-verifier
./gradlew :testing:parity:test                    # Maven and Gradle reports compared
./gradlew publishToItRepo                         # publish every module to build/it-repo
```

`functionalTest`, `mavenIT` and the parity test depend on `publishToItRepo`, so they test freshly built artifacts. Details: [docs/development.md](docs/development.md).

## Testing conventions

- JUnit 5 and AssertJ. No mocking framework for class loaders: use real jars (`core`'s `RuleJars`) and real class directories.
- Rule tests are fixture-driven: put the violating and allowed shapes in `rules-<backend>/src/test/java/archfixtures/<area>/`, import them with the backend, and assert on violation details plus `scopes()` and `severity()`. `FixtureContexts` builds a real context for a rule test.
- Module-level test resources encode services-file cases. Add a directory instead of mocking a class loader.
- Test the behaviour a consumer can see: violation detection, thresholds, registration failures, report lines, empty-view failures. Skip tests that only restate wiring or constants.
- A user-visible bug gets a regression fixture or test that reproduces the failure and is labelled as a regression guard.
- JaCoCo requires 80 % line coverage in `api`, `core`, `context-*` and `rules-*`.

## Repository hygiene

- Gitignored, never commit: `build/`, `.gradle/`, `.idea/`, IDE metadata, `.omc/`. `.superpowers/` is local agent scratch.
- Commits use Conventional Commits (`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`, `ci`). Work lands on `main` through pull requests from a dedicated branch.
- Release Please reads the commit types to choose versions, so use them accurately; a breaking change needs `!` or a `BREAKING CHANGE` footer.

## Style

- Java 21, formatted by Spotless with palantir-java-format. Run `./gradlew spotlessApply` and re-check that the edit landed.
- Checkstyle (`config/checkstyle/checkstyle.xml`) covers imports, braces, `equals`/`hashCode`, final and utility classes and tabs. Formatting is Spotless's job.
- `Objects.isNull(x)` / `Objects.nonNull(x)` instead of `== null` / `!= null`.
- Comments only where intent is not obvious from the code. No Javadoc for the linter's sake.
- Prefer small final classes with private constructors for stateless helpers, records for result rows, and text blocks for multi-line user-facing messages.

## Documentation obligations

A change that alters user-visible behaviour is not done until its docs match:

| Changed | Update |
|---|---|
| A rule, its severity or its parameters | `docs/rules/<rule>.md` + `.ru.md`, README rule table (both languages) |
| Plugin parameters, thresholds, multi-module setup, report format | `docs/configuration.md` + `.ru.md`, and the README example output if it changed |
| Build, tests, local commands, the pre-push hook | `docs/development.md` + `.ru.md` |
| Release flow | `docs/releasing.md` + `.ru.md` |
| Questions users will ask | `docs/faq.md` + `.ru.md` |
| A decision that changes a contract | a new ADR in `docs/adr/` (Context / Decision / Consequences), superseding the old one if needed |

`DocsLinkTest` fails the build when a relative link breaks, a README table names a rule that does not exist, or the two READMEs list different rules.

Each rule page follows the same shape: title, language switch, `FQCN · SEVERITY · scopes` (the two JavaParser/ClassGraph pages add the backend), why it matters, a violation and fix example, a **Register** block with the class name, a **Configure** block (subclassing, or why there is nothing to configure), and a link back to the README table.

## Definition of done

1. `./gradlew build` is green.
2. New or changed consumer-visible behaviour is exercised: a rule test with fixtures, or a shared fixture for plugin-level behaviour.
3. Messages stay project-neutral and actionable; report or message changes update the expectations.
4. Every touched doc page has its `.ru.md` counterpart updated, and the README tables agree with the code.
5. No hand-bumped version, no build output and no scratch directory added to git, and the denylist check prints nothing.
