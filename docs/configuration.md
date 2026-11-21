# Configuration

## Parameters

| Parameter | Default | Maven property | Gradle property |
|---|---|---|---|
| `rootPackage` | required | `archgate.rootPackage` | `-Parchgate.rootPackage` |
| `failOn` | `WARNING` (`INFO`, `WARNING`, `ERROR`, `NEVER`) | `archgate.failOn` | `-Parchgate.failOn` |
| `modules` | Maven: the current module plus the reactor modules it directly depends on, except `pom` packaging; entries are artifactIds and a list replaces the default entirely. Gradle: the current project; `modules(...)` adds the listed projects to it | — | — |
| `skip` | `false` | `archgate.skip` | `-Parchgate.skip` |

The gate fails when any finding has a severity at or above `failOn`. With `NEVER` it only reports. For example, `-Darchgate.failOn=ERROR` (Maven) or `-Parchgate.failOn=ERROR` (Gradle) lets warnings through. `UnableToVerifyError` fails the build whatever `failOn` says, `NEVER` included.

The Maven plugin is the goal `check` of `dev.quokkify.archgate:archgate-maven-plugin`, bound to `verify`. The Gradle plugin has the id `dev.quokkify.archgate` and registers the task `archgateCheck`.

## Gradle

<!-- x-release-please-start-version -->
```groovy
plugins {
    id 'java'
    id 'dev.quokkify.archgate' version '0.3.0'
}

archgate {
    rootPackage = 'com.acme'
    failOn = 'WARNING'
    skip = false
    modules(':api', ':domain')
}
```
<!-- x-release-please-end -->

Supported consumer Gradle version: 9.7.1 and newer. Older versions are not tested.

- The project that applies the plugin must apply `java`, or a plugin that applies it (`java-library`, `groovy`, `scala`, `org.jetbrains.kotlin.jvm`). Without it, configuration fails with `applies dev.quokkify.archgate but not the java plugin`, unless the project sets `skip = true`. A root project without sources cannot aggregate other projects; make a java project the gate and list them in `modules(...)`. Android projects are not supported.
- `archgateCheck` (group `verification`) depends on `testClasses` and is wired into `check`.
- The task is safe for the configuration cache. On a second run with unchanged inputs it is `UP-TO-DATE`, unless a rule obtained `Environment` or was skipped in the previous run; then it runs again. The task is not stored in the build cache, because rules may read external services ([ADR 0009](adr/0009-archgate-check-not-build-cached.md)).
- The report is written to `build/reports/archgate/report.txt`. The console report is printed only when the task executes; it is not replayed on `UP-TO-DATE`, so read the report file in that case.
- A failing gate throws a `GradleException` whose message is the counts line, for example `Architecture gate failed: 2 rules, 1 error; failure policy: failOn=WARNING`; the report itself goes to the console and the report file. When a rule cannot be verified, the message is the reason, and no report is written.

### Precedence

Both plugins follow one rule: a value set in the build file wins, and the command-line property is only a default for a parameter the build file leaves unset.

| Parameter | Order, highest first |
|---|---|
| `rootPackage` | `archgate { rootPackage = … }`, then `-Parchgate.rootPackage`; unset fails the build |
| `failOn` | `archgate { failOn = … }`, then `-Parchgate.failOn`, then `WARNING` |
| `skip` | `archgate { skip = … }`, then `-Parchgate.skip`, then `false` |

Maven behaves the same: `<configuration>` in the POM wins over `-Darchgate.rootPackage`, `-Darchgate.failOn` and `-Darchgate.skip`. To drive a parameter from the command line, leave it out of the build file.

### `rootPackage` is required

On a project that is not skipped, an unset `rootPackage` fails the build. A module that only exposes its classes to a gate in another module sets:

```groovy
archgate {
    skip = true
}
```

### Selecting modules

Without `modules`, only the current project is checked. This differs from Maven on purpose: Gradle has no cheap, correct way to reach the test classes of sibling projects. List the projects to include explicitly:

```groovy
archgate {
    rootPackage = 'com.acme'
    modules(':api', ':domain')
}
```

- Every project named in `modules(...)` must apply `dev.quokkify.archgate` itself, usually with `skip = true`. Otherwise the task fails with a message that contains `does not apply dev.quokkify.archgate`.
- Each such project exposes its main and test class directories (every directory of the source set's output, so classes compiled by the Groovy, Scala or Kotlin plugins are included), their processed-resources directories (the service-file rules read `META-INF/services` from them) and source directories through a consumable configuration, and the gate project reads them.
- Known limitation: the source directories of sibling projects are captured at configuration time. A source directory added to a sibling project after its configuration has finished is not seen by the gate.

### Class loading

The rules run in a Gradle worker with class loader isolation. The worker classpath is the plugin's own `core` and `api`, followed by the project's `testRuntimeClasspath`, so the plugin's `api` takes precedence over a copy on the project classpath.

## Maven

| Parameter | Default | Property |
|---|---|---|
| `rootPackage` | required | `archgate.rootPackage` |
| `failOn` | `WARNING` | `archgate.failOn` |
| `modules` | the current module plus the reactor modules it directly depends on, except those with `pom` packaging | — |
| `skip` | `false` | `archgate.skip` |

- The minimum Maven version is 3.9.0.
- The goal runs in `verify` and also with `-DskipTests`.
- The report is also written to `target/archgate/report.txt`.
- A failing gate fails the build with a `MojoFailureException`; a registration problem or another unable-to-verify condition fails it with a `MojoExecutionException`.
- `modules` replaces the default selection entirely. Entries are the `artifactId`s of reactor modules. The current module is checked only if you list it; an unknown entry fails the build.

## Version alignment

Import `archgate-bom` so that every ArchGate artifact resolves to the same version as the plugin. On the plugin's class loader, its own `archgate-api` takes precedence.

Maven:

<!-- x-release-please-start-version -->
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>dev.quokkify.archgate</groupId>
      <artifactId>archgate-bom</artifactId>
      <version>0.3.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```
<!-- x-release-please-end -->

Gradle:

<!-- x-release-please-start-version -->
```groovy
dependencies {
    testImplementation platform('dev.quokkify.archgate:archgate-bom:0.3.0')
}
```
<!-- x-release-please-end -->

### Rule libraries

A jar that ships rules declares, in `META-INF/MANIFEST.MF`, the `archgate-api` version it was compiled against.

Maven:

<!-- x-release-please-start-version -->
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-jar-plugin</artifactId>
  <configuration>
    <archive>
      <manifestEntries>
        <Archgate-Api-Version>0.3.0</Archgate-Api-Version>
      </manifestEntries>
    </archive>
  </configuration>
</plugin>
```
<!-- x-release-please-end -->

Gradle:

<!-- x-release-please-start-version -->
```groovy
tasks.named('jar') {
    manifest {
        attributes('Archgate-Api-Version': '0.3.0')
    }
}
```
<!-- x-release-please-end -->

`archgate-api` declares its own version the same way, and so does every `archgate-rules-*` jar. Before any rule is created, the engine compares each library's value with the running `archgate-api`. If a library needs a newer API, or its value is not a version, the build fails and names the jar. See [ADR 0002](adr/0002-api-version-manifest.md) for the reasoning.

- Format: `X.Y.Z` or `dev-SNAPSHOT`. `dev-SNAPSHOT` counts as newer than every release. Release builds of ArchGate stamp the release version; every `-SNAPSHOT` build stamps `dev-SNAPSHOT`.
- A plugin that is newer than the value keeps working. Before raising the value, go through the checklist below.
- A rule is checked together with its superclasses, so a subclass of a built-in rule in your module still checks the `archgate-rules-*` jar it extends. Jars without the attribute are not checked.

### Raising archgate-api in a rule library

A rule library stamps the `archgate-api` version it builds against, so raising it raises the minimum plugin version of every consumer. Approve the update (for example the Renovate Dependency Dashboard checkbox) only when every item holds:

- [ ] The [release notes](https://github.com/quokkify/archgate/releases) between the current and the new version list no removed or changed `archgate-api` method that the library's rules use.
- [ ] Every consumer of the library already runs the ArchGate plugin at the new version or newer, or is ready to raise it together with the library; an older plugin fails with "need a newer ArchGate API".
- [ ] The library's own build is green on the update branch.

If an item does not hold, leave the update unapproved; nothing breaks while the library stays on its current version.

## Registration

The services file `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule` is the full list of rules that run. Before any rule runs, the engine checks this list. The build fails in these cases:

| Condition | The message names |
|---|---|
| A public, concrete rule with a no-arg constructor exists in your module, but it is not listed | the class and the line to add |
| The file is missing or empty | the expected file path |
| A listed class does not exist, is abstract, or cannot be created | the line and the cause |
| No context provider is registered for the backend a rule needs | the `archgate-context-<backend>` artifact to add |

Rules from libraries, such as `archgate-rules-archunit`, run only when you list them.

Nested rule classes count too. A `public static` rule class nested in another class is checked like a top-level one and must be listed by its binary name, with `$` before the nested name: `com.acme.tests.arch.Rules$NoFooRule`.

## Multi-module projects

Put the gate in a dedicated last module named `<project>-archgate-rules`, using the project name as the prefix. For example, use `platform-archgate-rules` for the `platform` project. It depends on the modules it checks, and it holds the plugin, the services file and the project's own rules.

```
platform/
├── api/
├── tests/
└── platform-archgate-rules/       depends on api and tests
    ├── pom.xml | build.gradle     plugin + archgate-rules-*
    └── src/test/
        ├── java/…/rules/          project rules
        └── resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule
```

- The gate module may contain only `src/test`.
- The modules it checks must be built, or the build fails.
- Maven: each selected module's `target/classes` is imported as MAIN and its `target/test-classes` as TEST. The plugin finds them through the Maven reactor.
- Gradle: list the checked projects in `modules(...)`, see [Selecting modules](#selecting-modules).

## Always failing, whatever `failOn` says

- Maven: a selected module other than the gate module has no `target/classes` (it is not built). Gradle has no such check: `archgateCheck` depends on the classes of every project in `modules(...)`, so Gradle builds them first, and a project without main classes simply adds nothing to the MAIN view.
- The MAIN or TEST view finds no class under `rootPackage`, and a rule needs that view. A rule that reads both MAIN and TEST checks the one that has classes and fails only when both are empty.
- A `modules` entry is not part of the build (Maven: not in the reactor; Gradle: does not apply the plugin).
- A registration failure, as described above.
- A rule throws something other than a violation.
- An external rule cannot run in CI: variables are missing, or the service does not answer and `archgate.ci.serviceUnavailable` is not `skip`. See [Rules that call external services](#rules-that-call-external-services).

## Rules that call external services

A rule can need something from outside the build: a token, a URL or a service that has to answer. It reads variables through `context.environment()`, see [Writing a rule that needs variables or a service](extending.md#rules-that-need-variables-or-a-service). This section describes how the build treats such a rule.

### Where variables come from

The first non-blank value wins; an unset or blank value counts as missing.

- Maven: Maven user properties (`-DNAME=value` on the command line, or a line in `.mvn/maven.config`), then JVM system properties, then environment variables.
- Gradle: system properties of the build JVM (`gradle -DNAME=value`), then environment variables. `-PNAME=value` is not read for rule variables; only the three switches below also accept it.

A required variable is read with `require(names...)`, which lists every missing name at once: `Missing A, B. Pass them as -DNAME=value or as environment variables.` A variable the rule can run without is read with `optional(name)`. It returns an empty value when unset or blank, never skips the rule, and is not affected by `archgate.offline`.

### CI, offline and service failures

The gate treats a run as CI when the `CI` variable is set to anything other than `false`. Set `archgate.ci=true` or `archgate.ci=false` to override that. Any other value fails the build. A CI system that does not set `CI` must pass `archgate.ci=true`; otherwise the run counts as local and an external rule that cannot run is skipped instead of failing the build.

| Property | Values | Effect |
|---|---|---|
| `archgate.ci` | `true`, `false` | Overrides the `CI` variable. |
| `archgate.offline` | `true`, `false` | `true` skips every external rule outside CI. In CI it is ignored and the build logs `archgate.offline=true is ignored in CI (<source>); external rules run.` |
| `archgate.ci.serviceUnavailable` | `fail` (default), `skip` | In CI, `skip` reports a rule as skipped when its service does not answer. It applies only to that case, never to missing variables, and has no effect outside CI. |

These three are plain properties: there is no plugin parameter and no `archgate { }` property for them. Maven reads them like variables, for example `-Darchgate.ci=true`. Gradle accepts `-Parchgate.ci=true` like every other `archgate.*` setting, or `-Darchgate.ci=true`; a `-P` value wins over a `-D` value. They are read when the gate starts, so an invalid value fails the build even when no rule uses `Environment`; for example `Unknown archgate.ci.serviceUnavailable value 'maybe'. Use fail or skip.`

What happens when a rule cannot get what it needs:

| Situation | Outside CI | In CI |
|---|---|---|
| Required variables are missing | `[SKIP]`, the build continues | The build fails: `Rule '<name>' cannot run in CI (<source>): Missing A, B. ...` |
| `archgate.offline=true` | `[SKIP]` | Ignored, the rule runs (a warning is logged) |
| The service does not answer | `[SKIP]` | The build fails, or `[SKIP]` with `archgate.ci.serviceUnavailable=skip` |
| A rule finds a violation, or throws anything else | Same as always | Same as always |

A skipped rule never fails the gate. A rule that cannot run in CI fails it always, whatever `failOn` says.

### Gradle caching

`archgateCheck` is not stored in the build cache, because a rule may read an external service. It is `UP-TO-DATE` on a second run with unchanged inputs only when the rules of the previous run did not touch the environment. A run in which a rule obtained `context.environment()`, for `require`, `optional` or `unavailable`, or in which any rule was skipped, always runs again. A rule that calls a service should therefore obtain the environment before the call, not only when the call fails. See [ADR 0009](adr/0009-archgate-check-not-build-cached.md).

## Sharing a load between rules

Rules that need the same expensive input, such as the answer of an outside service, can load it once through `context.shared(name, build)`; see [Rules that share a load](extending.md#rules-that-share-a-load) for the API. "Once" means once per gate execution: the gate aggregates every selected module, so a multi-module build with one gate module loads the value once for all of them.

- Concurrent rules wait for the first caller and get the same object.
- A failed build is kept. Every rule that uses the name gets the same failure, and the build is not repeated, so a service that is down is asked once. If the failure is an `unavailable(...)` signal, every rule that uses the value is skipped under the rules in [CI, offline and service failures](#ci-offline-and-service-failures). If it is an `AssertionError` or `ArchitectureViolation`, it becomes a finding in each of those rules.
- The time spent in `shared` is not part of the rules' own durations; the report shows it on the `Shared:` line, see [Reading the report](#reading-the-report).

## Checking only the files a pull request changed

On a pull request, a rule that calls an external service can check only the files the pull request touched, which saves requests. The convention is one optional variable, `archgate.changedFiles`: the path to a UTF-8 file with one repository-relative path per line. The engine never reads this file. It reaches a rule through `context.environment().optional("archgate.changedFiles")`, available since the release that added external-service support; see [Rules that call external services](#rules-that-call-external-services). A rule opts in; a rule that does not look at the variable always checks the whole project.

| `archgate.changedFiles` | What a rule that supports it does |
|---|---|
| Unset or blank | Checks the whole project. |
| Names a file | Checks only the classes whose source file is listed. Makes no request and passes when none of its files changed. |
| Names a file that cannot be read | Fails. The caller asked for a partial check that cannot be done, so passing would hide that. |

A rule that shares its load can show the mode in the shared-value name, for example `Shared: issue index (changed files) 1583 ms`.

### Who sets it

The CI pipeline of the consuming project sets it. A generic recipe for a pull request pipeline:

```
git diff --name-only <base>...HEAD -- '*.java' > changed.txt
mvn verify -Darchgate.changedFiles=changed.txt      # Maven
./gradlew check -Darchgate.changedFiles=changed.txt # Gradle
```

Leave it unset, so that the whole project is checked, when:

- the pipeline is not a pull request pipeline;
- build files change (`pom.xml`, `.mvn/`, Gradle build files, CI configuration), because such a change can affect every class;
- the diff cannot be computed.

Nothing sets it locally, so a local build runs the full check.

## Reading the report

```
============================================================
Architecture verification (2 rules, 8.1 s total)
============================================================
[PASS]  MAIN+TEST     41 ms  No Thread.sleep
[ERROR] TEST          18 ms  Tests do not call RestAssured directly
    Tests call RestAssured only through steps
    Violations (1):
        - Method <com.acme.tests.LoginTest.login()> calls method <io.restassured.RestAssured.given()> in (LoginTest.java:21)
Model build: ArchUnitContext 0 ms, main 380 ms, test 210 ms
Shared: issue index 120 ms
============================================================
Gate FAILED: 2 rules, 1 error
Failure policy (failOn=WARNING):
  ERROR    fails the build
  WARNING  fails the build
  INFO     reported only
```

Each line shows the status, the class views the rule checks, its duration and its name. When a rule over MAIN and TEST found one of them empty, for example in a module without tests, it checks the other one and its line ends with the skipped scope, for example `No Thread.sleep (TEST not checked: empty)`. The verdict line then counts such rules, for example `Gate PASSED: 7 rules (2 partially checked), no findings`; see [Partial checks](#partial-checks). Passed rules come first, findings last, so the violations are easy to spot when scrolling. Violations are listed below the rule. Rules run in parallel, and the results are sorted by rule name. A rule row and its detail lines form one block. In the Maven log only the row carries the `[INFO]`, `[WARNING]` or `[ERROR]` prefix, so the details stay attached to it; the report file always uses LF line endings. The `Model build:` line shows how long each analysis model took to build. The `Shared:` line follows it when rules used `context.shared`, and shows how long each shared value took to build, once per name, including builds that failed. A rule that reports its findings in groups prints each non-empty group as `Violations (N) "<title>":` followed by its entries; empty groups are left out. The verdict line repeats the counts that decided it, and the failure policy table shows for each severity whether it fails the build with the current `failOn`.

A rule that could not reach what it needs outside the build is shown as `[SKIP]` with two detail lines: what the rule reported, and why the run treats it as a skip.

```
[SKIP]  TEST           0 ms  tracker-check
    Missing TRACKER_USER, TRACKER_PASSWORD. Pass them as -DNAME=value or as environment variables.
    Skipped because this is not a CI run (no CI variable set); in CI this fails the build.
```

The second line varies: `Skipped because this is not a CI run (archgate.ci=false); in CI this fails the build.` outside CI, and `Skipped in CI because the service is unavailable (archgate.ci.serviceUnavailable=skip); the rule checked nothing.` when that property lets the service case skip. For `archgate.offline=true` the first line reads `External rules are switched off by archgate.offline=true.` The verdict line gets a count, for example `Gate PASSED: 3 rules, 1 info, 1 skipped`. A report with a skip is logged at WARNING at least, so a skipped rule is easy to see in the build log. A skip is not a pass: the rule checked nothing.

Maven and Gradle print the same text for the same sources, rules and `failOn`.

## Partial checks

A rule over MAIN and TEST checks only MAIN when the TEST view has no class under `rootPackage`, and the other way round. Its report line ends with `(TEST not checked: empty)` and the verdict line counts it as partially checked. This happens when:

- a checked module has no tests, which is the case this behaviour exists for;
- the test classes are not built, for example Gradle with `-x compileTestJava` or `-x testClasses`: `processTestResources` still copies the services file, so the gate runs with an empty TEST view;
- the tests live outside `rootPackage`.

Maven with `-Dmaven.test.skip=true` skips test resources too, so in a clean build the services file is missing and the gate fails with "no rules registered" rather than checking part of the code. `-DskipTests` compiles the tests and checks both views.

To fail on an empty view instead, subclass the rule and override `requireAllScopes()` to return `true` (see [Writing a backend or rule](extending.md#rules)). When every scope of a rule is empty, the build fails whatever the rule says.
