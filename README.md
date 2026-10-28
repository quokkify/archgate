<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner/archgate-banner-dark.png">
  <img src="docs/assets/banner/archgate-banner-light.png" alt="ArchGate — Architecture rules as a build gate" width="1280" height="320">
</picture>

![Java 21](https://img.shields.io/badge/Java-21-3FA66B?logo=openjdk&logoColor=white)
![Gradle 9.7+](https://img.shields.io/badge/Gradle-9.7%2B-3FA66B?logo=gradle&logoColor=white)
![Maven 3.9+](https://img.shields.io/badge/Maven-3.9%2B-3FA66B?logo=apachemaven&logoColor=white)
![ArchUnit 1.x](https://img.shields.io/badge/ArchUnit-1.x-3FA66B)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-3FA66B)](LICENSE)

English · [Русский](README.ru.md)

ArchGate runs architecture rules as a step of the Maven or Gradle build. It stops the build when the code breaks a rule.
- A rule runs only if your project lists it, built-in rules included.
- You can mix the built-in rules with rules you write yourself.
- Rules are backend-neutral: the built-in rules use [ArchUnit](https://www.archunit.org/), [JavaParser](https://javaparser.org/) or [ClassGraph](https://github.com/classgraph/classgraph), and each backend lives in its own module.

## Built-in rules

| Rule | Backend | Severity | Checks | Docs |
|---|---|---|---|---|
| `NoThreadSleepRule` | ArchUnit | ERROR | main + test | [No Thread.sleep](docs/rules/no-thread-sleep.md) |
| `NoConsoleOutputRule` | ArchUnit | ERROR | main | [No console output](docs/rules/no-console-output.md) |
| `NoJavaUtilDateRule` | ArchUnit | WARNING | main + test | [No java.util.Date](docs/rules/no-java-util-date.md) |
| `LoggerFieldNameRule` | ArchUnit | ERROR | main | [Logger field name](docs/rules/logger-field-name.md) |
| `TestClassNamingRule` | ArchUnit | ERROR | test | [Test class naming](docs/rules/test-class-naming.md) |
| `ClassLocationBySuffixRule` | ArchUnit | ERROR | main | [Class location by suffix](docs/rules/class-location-by-suffix.md) |
| `ServiceRegistrationRule` | ArchUnit | ERROR | main + test | [Service registration](docs/rules/service-registration.md) |
| `NoDisabledTestsRule` | ArchUnit | ERROR | test | [No disabled tests](docs/rules/no-disabled-tests.md) |
| `NoDeprecatedApiUsageRule` | ArchUnit | WARNING | main + test | [No deprecated API usage](docs/rules/no-deprecated-api-usage.md) |
| `NoGenericThrowsRule` | ArchUnit | WARNING | main | [No generic throws](docs/rules/no-generic-throws.md) |
| `TestAssertionsRule` | ArchUnit | WARNING | test | [Test assertions](docs/rules/test-assertions.md) |
| `HashCodeEqualsTogetherRule` | ArchUnit | ERROR | main | [hashCode and equals together](docs/rules/hash-code-equals-together.md) |
| `UtilityClassFinalityRule` | ArchUnit | WARNING | main | [Utility class finality](docs/rules/utility-class-finality.md) |
| `MaxMethodParametersRule` | ArchUnit | WARNING | main + test | [Max method parameters](docs/rules/max-method-parameters.md) |
| `NoWildcardImportsRule` | JavaParser | WARNING | main + test | [No wildcard imports](docs/rules/no-wildcard-imports.md) |
| `ServiceFileConsistencyRule` | ClassGraph | ERROR | main + test | [Service file consistency](docs/rules/service-file-consistency.md) |

Each backend ships as a pair of artifacts in group `dev.quokkify.archgate`:

| Backend | Rules artifact (package) | Context artifact |
|---|---|---|
| ArchUnit | `archgate-rules-archunit` (`dev.quokkify.archgate.rules.archunit`) | `archgate-context-archunit` |
| JavaParser | `archgate-rules-javaparser` (`dev.quokkify.archgate.rules.javaparser`) | `archgate-context-javaparser` |
| ClassGraph | `archgate-rules-classgraph` (`dev.quokkify.archgate.rules.classgraph`) | `archgate-context-classgraph` |

A rules artifact depends on its context artifact, so adding the rules is enough to run them. Add the context on its own to write your own rules for that backend. If no context provider for a rule's backend is on the classpath, the build fails with `No ContextProvider for … is registered on the classpath. Add dev.quokkify.archgate:archgate-context-<backend> as a test dependency.`

## Use with Maven

**1. Add the plugin and the rules to the pom** of the module that holds your tests (Maven 3.9.0 or newer):

<!-- x-release-please-start-version -->
```xml
<dependencies>
  <dependency>
    <groupId>dev.quokkify.archgate</groupId>
    <artifactId>archgate-rules-archunit</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>dev.quokkify.archgate</groupId>
      <artifactId>archgate-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions><execution><goals><goal>check</goal></goals></execution></executions>
      <configuration>
        <rootPackage>com.acme.tests</rootPackage>
      </configuration>
    </plugin>
  </plugins>
</build>
```
<!-- x-release-please-end -->

**2. List the rules to run** in `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`:

```
dev.quokkify.archgate.rules.archunit.NoThreadSleepRule
dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule
```

**3. Run** `mvn verify`. The gate runs in the `verify` phase, and also with `-DskipTests`:

```
[PASS]  MAIN+TEST     41 ms  No Thread.sleep
[PASS]  MAIN          12 ms  No console output
============================================================
Gate PASSED: 2 rules, no findings
Failure policy (failOn=WARNING):
  ERROR    fails the build
  WARNING  fails the build
  INFO     reported only
```

The report is also written to `target/archgate/report.txt`.

## Use with Gradle

Requires Gradle 9.7.1 or newer.

**1. Apply the plugin and add the rules** in the `build.gradle` of the module that holds your tests:

<!-- x-release-please-start-version -->
```groovy
plugins {
    id 'java'
    id 'dev.quokkify.archgate' version '0.1.0'
}

archgate {
    rootPackage = 'com.acme.tests'
    failOn = 'WARNING'            // or -Parchgate.failOn=ERROR
    skip = false                  // or -Parchgate.skip=true
    modules(':api', ':domain')    // optional
}

dependencies {
    testImplementation platform('dev.quokkify.archgate:archgate-bom:0.1.0')
    testImplementation 'dev.quokkify.archgate:archgate-rules-archunit'
}
```
<!-- x-release-please-end -->

**2. List the rules to run** in `src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule`, the same file as for Maven.

**3. Run** `./gradlew check`. The task `archgateCheck` (group `verification`) depends on `testClasses`, is wired into `check` and prints the same report as the Maven plugin. The report is also written to `build/reports/archgate/report.txt`. The task is cacheable and compatible with the configuration cache.

`rootPackage` is required. Modules in `modules(...)` must apply the plugin themselves, and modules that only expose classes to a gate set `archgate { skip = true }`. See [Configuration](docs/configuration.md#gradle).

`modules` means different things in the two plugins:

| | Maven | Gradle |
|---|---|---|
| Not set | The current module plus the reactor modules it directly depends on, except `pom` packaging | Only the current project |
| Set | Replaces the default: only the listed `artifactId`s, the current module only if listed | Adds the listed projects to the current project |

To change a parameter or the severity of a built-in rule, subclass it and register the subclass instead. Each rule page shows how.

## Write your own rule

For a multi-module project, keep custom rules in a dedicated last module named `<project>-archgate-rules`, using the project name as the prefix. For example, use `platform-archgate-rules` for the `platform` project. See [Configuration → Multi-module projects](docs/configuration.md#multi-module-projects).

**1. Add the backend context** as a test dependency (it brings `archgate-api`):

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.quokkify.archgate</groupId>
  <artifactId>archgate-context-archunit</artifactId>
  <version>0.1.0</version>
  <scope>test</scope>
</dependency>
```
<!-- x-release-please-end -->

**2. Implement `ArchitectureRule`** in `src/test/java`. The type parameter is the context of the backend the rule uses:

```java
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;

public class NoRestAssuredInTestsRule implements ArchitectureRule<ArchUnitContext> {

  @Override
  public String name() {
    return "Tests do not call RestAssured directly";
  }

  @Override
  public Severity severity() {
    return Severity.ERROR;
  }

  @Override
  public Class<ArchUnitContext> contextType() {
    return ArchUnitContext.class;
  }

  @Override
  public void verify(ArchUnitContext context) {
    ArchRule rule = noClasses().that().resideInAPackage(context.rootPackage() + ".tests..")
        .should().dependOnClassesThat().resideInAPackage("io.restassured..")
        .allowEmptyShould(true);
    checkViolations("Tests call RestAssured only through steps", ArchRuleChecks.violations(rule, context.test()));
  }
}
```

**3. Register it** in the same services file. List nested `public static` rule classes too, by their binary name (`Outer$Inner`):

```
com.acme.tests.arch.NoRestAssuredInTestsRule
```

If you write a rule but forget to register it, the build fails and tells you which line to add.

Context available inside `verify`, per backend:

| Context | Method | Returns |
|---|---|---|
| `ArchUnitContext` | `rootPackage()` | the configured `rootPackage` |
| | `main()`, `test()`, `all()` | compiled classes under `rootPackage` (ArchUnit `JavaClasses`) |
| | `layout()` | the `ProjectLayout`: class and source directories per scope |
| `JavaParserContext` | `compilationUnits(Scope)` | parsed sources of the scope (JavaParser `CompilationUnit`) |
| | `relativePath(unit)` | the path of the unit relative to its source directory |
| `ClassGraphContext` | `scan(Scope)` | the ClassGraph `ScanResult` of the scope's class directories |
| | `layout()` | the `ProjectLayout` |

A rule reports violations by throwing `ArchitectureViolation`, which `checkViolations` does for you, or an ArchUnit `AssertionError`. If a rule has nothing to check, for example an empty class view, it throws `UnableToVerifyError`. That always fails the build.

## More

- [Configuration](docs/configuration.md): plugin parameters for Maven and Gradle, `failOn`, multi-module projects, how to read the report.
- [Writing a backend or rule](docs/extending.md): the contract for rules and backends: concurrency, shared contexts, errors, what is public.
- [Development](docs/development.md): building locally, tests, the pre-push hook.
- [Releasing](docs/releasing.md): how a release is cut and published.
- [FAQ](docs/faq.md) ([на русском](docs/faq.ru.md)): answers to common questions.
- [Architecture decisions](docs/adr/0001-opt-in-rule-registration.md): why the design is shaped this way.

Licensed under the [Apache License, Version 2.0](LICENSE). Copyright 2026 quokkify.
