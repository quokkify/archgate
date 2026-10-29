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
    id 'dev.quokkify.archgate' version '0.1.1'
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

- `archgateCheck` (group `verification`) depends on `testClasses` and is wired into `check`.
- The task is cacheable and safe for the configuration cache. On a second run with unchanged inputs it is `UP-TO-DATE`, and a build-cache hit is `FROM-CACHE`.
- The report is written to `build/reports/archgate/report.txt`. The console report is printed only when the task executes; it is not replayed on `UP-TO-DATE` or `FROM-CACHE`, so read the report file in those cases.
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
- Each such project exposes its main and test class directories, their processed-resources directories (the service-file rules read `META-INF/services` from them) and source directories through a consumable configuration, and the gate project reads them.
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
      <version>0.1.1</version>
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
    testImplementation platform('dev.quokkify.archgate:archgate-bom:0.1.1')
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
        <Archgate-Api-Version>0.1.1</Archgate-Api-Version>
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
        attributes('Archgate-Api-Version': '0.1.1')
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
- The MAIN or TEST view finds no class under `rootPackage`, and a rule needs that view.
- A `modules` entry is not part of the build (Maven: not in the reactor; Gradle: does not apply the plugin).
- A registration failure, as described above.
- A rule throws something other than a violation.

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
============================================================
Gate FAILED: 2 rules, 1 error
Failure policy (failOn=WARNING):
  ERROR    fails the build
  WARNING  fails the build
  INFO     reported only
```

Each line shows the status, the class views the rule checked, its duration and its name. Passed rules come first, findings last, so the violations are easy to spot when scrolling. Violations are listed below the rule. Rules run in parallel, and the results are sorted by rule name. The `Model build:` line shows how long each analysis model took to build. The verdict line repeats the counts that decided it, and the failure policy table shows for each severity whether it fails the build with the current `failOn`.

Maven and Gradle print the same text for the same sources, rules and `failOn`.
