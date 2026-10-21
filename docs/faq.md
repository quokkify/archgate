# FAQ

English · [Русский](faq.ru.md)

## Contents

- [Basics](#basics)
- [Using ArchGate](#using-archgate)
- [Gradle](#gradle)
- [Build](#build)
- [Releases](#releases)

---

## Basics

### What is ArchGate?

A build gate for architecture rules. It is a Maven plugin and a Gradle plugin that run rules on your compiled classes or sources and fail the build when a rule is broken. The built-in rules use [ArchUnit](https://www.archunit.org/), [JavaParser](https://javaparser.org/) and [ClassGraph](https://github.com/classgraph/classgraph), each in its own module. You can use the built-in rules, write your own, or both.

### What do I need to use it?

Java 21, and either Maven 3.9.0 or newer or Gradle 9.7.1 or newer. Both TestNG and JUnit (Jupiter) projects work, because the rules check compiled classes and sources, not test runs.

### Does it slow the build down?

Only a little. Rules run in parallel, and each analysis model is built once, lazily, and shared by all rules that need it. The report shows the time of each rule and of each model, so slow rules are easy to spot.

### How do I run only the gate?

Maven: `mvn verify -DskipTests`. The tests are skipped, but test classes are still compiled, and the gate runs in `verify`. Gradle: `./gradlew archgateCheck`.

### How do I skip the gate temporarily?

Maven: `-Darchgate.skip`. Gradle: `-Parchgate.skip=true`, unless the build script sets `skip` explicitly. To let warnings through but keep errors failing, use `-Darchgate.failOn=ERROR` (Maven) or `-Parchgate.failOn=ERROR` (Gradle).

### How do I try a change that is not released yet?

Check out the branch and publish it to your local Maven repository with `./gradlew publishToMavenLocal`, then depend on the version in `version.txt` with the `-SNAPSHOT` suffix. The repository does not publish snapshots.

---

## Using ArchGate

### Do I have to write `META-INF/services` myself, or can rules be listed in the build file?

In `META-INF/services`. The file `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule` lists every rule that runs, built-in and your own. The build file holds only plugin settings (`rootPackage`, `failOn`, `modules`, `skip`). Listing class names in the build file would break silently on a rename and duplicate the code. See [ADR 0001](adr/0001-opt-in-rule-registration.md).

### Why does no rule run after I add `archgate-rules-archunit`?

That is by design: nothing runs until you list it. The rules artifacts ship rule classes, not a services file. Add the lines you want, as shown in [Use with Maven](../README.md#use-with-maven) or [Use with Gradle](../README.md#use-with-gradle).

### I wrote a rule, but the build fails with "not registered".

Add the class name that the message shows to the services file. The engine rejects public, concrete rules that exist in your module but are not listed, so a forgotten rule never passes silently. This includes `public static` rule classes nested in another class: list them by their binary name, `Outer$Inner`.

### The build fails with "No ContextProvider for … is registered".

A rule needs the analysis backend of its context, and its provider is not on the test classpath. Add the artifact named in the message, `dev.quokkify.archgate:archgate-context-<backend>`, as a test dependency. The built-in `archgate-rules-<backend>` artifacts bring their context with them; custom rules need the context added by hand.

### The build fails with "need a newer ArchGate API". What now?

A rule library on your test classpath was compiled against a newer `archgate-api` than your ArchGate plugin. Raise the plugin version to the version the message names. If the message says the value is invalid, the library's build writes a wrong `Archgate-Api-Version`; see [Rule libraries](configuration.md#rule-libraries). If a library declares `dev-SNAPSHOT`, it was built against unreleased code: rebuild it against a released `archgate-api`.

### How do I change a parameter or the severity of a built-in rule?

Subclass the rule, pass the parameters to its `protected` constructor or override `severity()`, then register the subclass instead of the original. Each [rule page](rules/) shows an example.

### Why a build plugin and not a JUnit/TestNG test?

- A test would be skipped with `-DskipTests`; the Maven goal runs in `verify` anyway.
- The plugins find the class and source directories themselves, so a consumer does not hand-write class paths.
- The same engine and the same report run under both build tools.

### How does it work in a multi-module project?

A dedicated last module named `<project>-archgate-rules` (for example `platform-archgate-rules` for the `platform` project) depends on the modules it checks and holds the plugin, the services file and your rules. That module can contain only `src/test`. See [Configuration → Multi-module projects](configuration.md#multi-module-projects).

### Does `NoWildcardImportsRule` or `ServiceFileConsistencyRule` need extra setup?

Only the matching rules artifact: `archgate-rules-javaparser` or `archgate-rules-classgraph`. Each brings its own context. See the rule pages: [No wildcard imports](rules/no-wildcard-imports.md) and [Service file consistency](rules/service-file-consistency.md).

---

## Gradle

### Why does the console show no report on the second run?

The task `archgateCheck` is cacheable. When its inputs are unchanged it is `UP-TO-DATE`, or `FROM-CACHE` after a cache hit, and Gradle does not run it, so it prints nothing. The report of the last real run is in `build/reports/archgate/report.txt`.

### The build fails with "does not apply dev.quokkify.archgate".

A project named in `archgate { modules(...) }` has not applied the plugin. Apply `dev.quokkify.archgate` there too. If the project only exposes its classes to the gate, set `archgate { skip = true }` in it.

### The build fails because `rootPackage` is not set.

`rootPackage` is required on every project that applies the plugin and is not skipped. Set it in the `archgate { }` block, or pass `-Parchgate.rootPackage=…`. Projects that only expose classes to a gate set `archgate { skip = true }` instead.

### Why does `-Parchgate.failOn=ERROR` have no effect?

An explicit `failOn` in the build script wins over the command-line property, which only supplies the default. `-Parchgate.rootPackage` is the other way round: it overrides the script. See [Precedence](configuration.md#precedence).

### Why are sibling modules not checked automatically?

Gradle has no cheap, correct way to reach the test classes of sibling projects, so `modules(...)` is explicit. See [ADR 0004](adr/0004-gradle-explicit-modules.md).

---

## Build

### Why is the Maven plugin built with Gradle?

The whole repository builds with Gradle, including the Maven plugin, whose descriptor is generated by `org.gradlex.maven-plugin-development`. See [ADR 0005](adr/0005-gradle-build-for-maven-plugin.md).

### Why is the minimum Maven version 3.9.0?

The plugin descriptor carries `<requiredMavenVersion>3.9.0</requiredMavenVersion>`, and `PluginDescriptorTest` fails the build if it disappears or moves by accident. The repository's integration tests run against a newer Maven 3.9.x distribution; that does not affect consumers.

### What does the version `-SNAPSHOT` mean?

`version.txt` holds the last released version. A normal build appends `-SNAPSHOT`; a release build passes `-Prelease=true` and uses the version as is. No version is bumped by hand.

---

## Releases

### How is a release made?

Release Please opens a release pull request from the commit history; merging it tags `vX.Y.Z` and creates the GitHub Release, and a publishing workflow then uploads the artifacts. See [Releasing](releasing.md).

### What is the first version?

`0.1.0`, forced with a `Release-As: 0.1.0` commit.

### How do I roll back a bad release?

Treat a published version as immutable. Consumers pin the previous version, and you release a fix-forward patch version. See [Releasing](releasing.md#rolling-back-a-bad-release).
