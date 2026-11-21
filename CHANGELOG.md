# Changelog

## [0.4.0](https://github.com/quokkify/archgate/compare/v0.3.0...v0.4.0) (2026-11-21)

<!-- project-toolkit:release-notes v2 -->
**At a glance:** ⚠️ 4 breaking · 3 new · 1 fixed · 2 internal

### ⚠️ Breaking changes

> [!WARNING]
> **core:** detect CI on Jenkins and TeamCity; catch more rule bypasses — Jenkins, TeamCity and other CI systems that do not set `CI` now count as CI, so an external rule that cannot run fails the build there. Built-in rules now catch method references, calls through subclasses and composed JUnit annotations. ([#69](https://github.com/quokkify/archgate/pull/69))
>
> 1. If your CI system is listed in `docs/configuration.md#how-ci-is-detected` and does not set `CI`, provide the variables your external rules need, such as the TestRail or TestPlanIt credentials, for the CI build.
> 2. If the service may be unreachable from CI and a skip is acceptable, pass `-Darchgate.ci.serviceUnavailable=skip`.
> 3. To keep the old lenient behaviour on purpose, pass `-Darchgate.ci=false` (Gradle: `-Parchgate.ci=false`).
> 4. Builds run on a machine that carries one of the listed variables, such as a Jenkins or TeamCity agent image used locally, now get CI behaviour. Pass `archgate.ci=false` to force local behaviour; `CI=false` is not enough.
> 5. Expect new findings in these cases:
>    - `NoThreadSleepRule`: `Thread::sleep`, `TimeUnit.X::sleep`, an unqualified `sleep(..)` in a `Thread` subclass, `SubThread.sleep(..)`.
>    - `NoConsoleOutputRule`: `Thread::dumpStack`, `Throwable::printStackTrace`, an unqualified `dumpStack()` in a `Thread` subclass.
>    - `NoDeprecatedApiUsageRule`: method and constructor references to deprecated members or signatures.
>    - `NoDisabledTestsRule`: tests disabled through a composed `@Disabled`.
>    - `TestAssertionsRule` and `TestClassNamingRule`: tests marked by a composed JUnit `@Test`.

> [!WARNING]
> **gradle:** gate no longer passes without checking anything — The Gradle plugin no longer passes without checking anything. It now scans sibling classes compiled by the Groovy, Scala or Kotlin plugins, fails when applied without the `java` plugin, and deletes the previous report when the gate is skipped. ([#71](https://github.com/quokkify/archgate/pull/71))
>
> 1. A project that sets `archgate { rootPackage … }` or `modules(...)` without the `java` plugin (for example a `base`-only root or an Android module) now fails at configuration with `applies dev.quokkify.archgate but not the java plugin`. Apply the plugin with `java` in the project whose `src/test/resources` lists the rules, and list other projects in `archgate { modules(...) }`. If the project only configures the plugin for others, set `archgate { skip = true }` in it; `-Parchgate.skip=true` skips the gate in every project.
> 2. If you apply the plugin to every subproject, apply it only where `java` is:
>
>    ```groovy
>    subprojects {
>        plugins.withId('java') {
>            apply plugin: 'dev.quokkify.archgate'
>        }
>    }
>    ```
>
>    Subprojects without `java` and without `archgate { }` settings keep working without this; they only log one line.
> 3. If a module in `modules(...)` has Groovy, Scala or Kotlin classes, they are now checked and may report new findings.

> [!WARNING]
> **gradle:** a project that applies dev.quokkify.archgate without the java plugin and without skip = true now fails at configuration time instead of silently checking nothing.

> [!WARNING]
> **core:** builds on Jenkins, TeamCity, Azure Pipelines, AWS CodeBuild or another listed system that does not set CI now fail when an external rule cannot run, where they used to pass with the rule skipped. Provide the rule's variables, set archgate.ci.serviceUnavailable=skip for an unreachable service, or pass archgate.ci=false to keep the old behaviour.

### ✨ New

- **rules:** add API-test hygiene rules — **Keep tests on typed models and layers apart.** Add `archgate-rules-archunit-api` and list `JsonTreeUsageRule` to stop tests from building JSON through the Jackson tree API; subclass `LayerBoundaryRule` to forbid dependencies from one package layer to others. 🤖 Generated with [Claude Code](https://claude.com/claude-code) ([#66](https://github.com/quokkify/archgate/pull/66))
- **rules:** add TmsLink rules for test-management links — **Check test-management links in tests.** Add `archgate-rules-archunit-tms` and list `TmsLinkRequiredRule`, `TmsLinkUniqueRule` or `TmsLinkFormatRule` in your services file to require, de-duplicate and validate `@TmsLink` ids on TestNG test methods. 🤖 Generated with [Claude Code](https://claude.com/claude-code) ([#64](https://github.com/quokkify/archgate/pull/64))
- **rules:** check TmsLink ids against TestRail and TestPlanIt — Check that every `@TmsLink` id points at a real case in your test-management system. Add `archgate-rules-archunit-testrail` or `archgate-rules-archunit-testplanit`, list the exists or automated rule, and set the connection variables; outside CI an unreachable server skips the rule. ([#67](https://github.com/quokkify/archgate/pull/67))

### 🐛 Fixed

- **maven:** fail when -pl or -rf leaves checked modules out of the run — **Maven:** `mvn -pl <gate>` and `mvn -rf :<gate>` no longer pass silently when the gate checks sibling modules that are not part of the run; the build now fails and names them. Add them to `-pl` or use `-am`, run without `-pl`/`-rf`, or pass `-Darchgate.skip=true` to skip the gate in that run. ([#68](https://github.com/quokkify/archgate/pull/68))

<details><summary>Internal changes (2 PRs)</summary>

**Maintenance**

- **renovate:** adopt the supply-chain hardening and stack grouping presets ([#72](https://github.com/quokkify/archgate/pull/72))
- **template:** update shared project template ([#73](https://github.com/quokkify/archgate/pull/73))

</details>

## [0.3.0](https://github.com/quokkify/archgate/compare/v0.2.1...v0.3.0) (2026-11-20)

<!-- project-toolkit:release-notes v2 -->
**At a glance:** 2 new · 10 internal

### ✨ New

- **rules:** let rules call external services and skip outside CI — **Rules can call external services.** Read credentials with `context.environment().require("TOKEN")`; outside CI a missing variable or an unreachable service shows `[SKIP]`, in CI it fails the build. Set `archgate.ci=true` on CI systems that do not set `CI`. ([#59](https://github.com/quokkify/archgate/pull/59))
  ```java
  @Override
  public void verify(ArchUnitContext context) {
      Map<String, String> vars = context.environment().require("TRACKER_URL", "TRACKER_TOKEN");
      try {
          checkViolations("Every case exists", findMissing(new TrackerClient(vars), context.test()));
      } catch (IOException e) {
          throw context.environment().unavailable("Tracker", e);
      }
  }
  ```

  🤖 Generated with [Claude Code](https://claude.com/claude-code)
- **rules:** share one load between rules and group violations — **Rules can share one load and group findings.** Wrap an expensive load in `context.shared("issue index", () -> load())` to build it once per run; report findings by reason with `checkViolationGroups`. ([#60](https://github.com/quokkify/archgate/pull/60))
  ```java
  IssueIndex index = context.shared("issue index", () -> IssueIndex.load(client));
  checkViolationGroups("Every case id identifies a live case", List.of(
          new ViolationGroup("case does not exist", missing(index)),
          new ViolationGroup("case is deleted", deleted(index))));
  ```

  🤖 Generated with [Claude Code](https://claude.com/claude-code)

<details><summary>Internal changes (10 PRs)</summary>

**Dependencies**

- actions/setup-java v6.1.0 → v6.2.0 ([#52](https://github.com/quokkify/archgate/pull/52))
- dev.quokkify:testng 0.12.2 → 0.15.0 ([#54](https://github.com/quokkify/archgate/pull/54), [#56](https://github.com/quokkify/archgate/pull/56))
- gradle 9.8.2 → 9.9.1 ([#53](https://github.com/quokkify/archgate/pull/53), [#57](https://github.com/quokkify/archgate/pull/57))
- org.apache.maven:apache-maven 3.10.0 → 3.10.1 ([#58](https://github.com/quokkify/archgate/pull/58))

**Maintenance**

- **config:** describe checking only the files a pull request changed ([#63](https://github.com/quokkify/archgate/pull/63))
- **deps:** move Querydsl test fixtures to the OpenFeign fork ([#50](https://github.com/quokkify/archgate/pull/50))
- **rules:** assert actionable naming-rule messages ([#62](https://github.com/quokkify/archgate/pull/62))
- **template:** update shared project template ([#55](https://github.com/quokkify/archgate/pull/55))

</details>

## [0.2.1](https://github.com/quokkify/archgate/compare/v0.2.0...v0.2.1) (2026-11-07)

<!-- project-toolkit:release-notes v2 -->
**At a glance:** 1 fixed · 1 internal

### 🐛 Fixed

- **rules:** report use of a deprecated class in NoDeprecatedApiUsageRule ([#49](https://github.com/quokkify/archgate/pull/49))

<details><summary>Internal changes (1 PR)</summary>

**Maintenance**

- **template:** update shared project template ([#47](https://github.com/quokkify/archgate/pull/47))

</details>

## [0.2.0](https://github.com/quokkify/archgate/compare/v0.1.2...v0.2.0) (2026-11-07)

<!-- project-toolkit:release-notes v2 -->
**At a glance:** 1 new · 7 internal

### ✨ New

- check the non-empty scopes of main + test rules ([#44](https://github.com/quokkify/archgate/pull/44))

<details><summary>Internal changes (7 PRs)</summary>

**Dependencies**

- com.diffplug.spotless:spotless-plugin-gradle 8.10.4 → 8.11.0 ([#41](https://github.com/quokkify/archgate/pull/41))
- gradle/actions v6.4.0 → v6.4.1 ([#38](https://github.com/quokkify/archgate/pull/38))
- io.github.classgraph:classgraph 4.8.199 → 5.0.2 · major ([#27](https://github.com/quokkify/archgate/pull/27))
- quokkify/ci-kit v4.1.0 → v4.2.0 ([#40](https://github.com/quokkify/archgate/pull/40))

**Maintenance**

- run tests as one job per Gradle project ([#37](https://github.com/quokkify/archgate/pull/37))
- stop Renovate from raising the Maven API floor ([#42](https://github.com/quokkify/archgate/pull/42))
- **template:** update shared project template ([#45](https://github.com/quokkify/archgate/pull/45))

</details>

## [0.1.2](https://github.com/quokkify/archgate/compare/v0.1.1...v0.1.2) (2026-10-29)


### 🐛 Bug Fixes

* **ci:** collect Allure results into one directory so the report is not empty ([#35](https://github.com/quokkify/archgate/issues/35)) ([7163e2f](https://github.com/quokkify/archgate/commit/7163e2f593601df3dfdda1e4f6d717de80384d3d))
* **publish:** wait up to an hour for Maven Central to publish ([#31](https://github.com/quokkify/archgate/issues/31)) ([5b1b82f](https://github.com/quokkify/archgate/commit/5b1b82f13d75c6487f7d775bc229b180460dd822))

## [0.1.1](https://github.com/quokkify/archgate/compare/v0.1.0...v0.1.1) (2026-10-29)


### 🐛 Bug Fixes

* **publish:** fail fast on missing secrets and pass the optional signing key ID ([#29](https://github.com/quokkify/archgate/issues/29)) ([48e89b2](https://github.com/quokkify/archgate/commit/48e89b2d384b46c06031244c07cf877803b148bd))

## 0.1.0 (2026-10-28)


### ✨ Features

* port ArchGate to Gradle with Maven and Gradle plugins ([5e6a14a](https://github.com/quokkify/archgate/commit/5e6a14a8d2a5d48d0ee4908e752e28ba6b4187f5))


### 📦 Dependencies

* **gradle-wrapper:** update gradle to v9.8.1 ([#6](https://github.com/quokkify/archgate/issues/6)) ([1e8b32d](https://github.com/quokkify/archgate/commit/1e8b32df510894fbe056d9ebf06b4c0573eaacec))
* **gradle-wrapper:** update gradle to v9.8.2 ([#12](https://github.com/quokkify/archgate/issues/12)) ([2130a02](https://github.com/quokkify/archgate/commit/2130a022b72f92fa05fecbd2152bb9dc12cfbdcb))
* **gradle:** update org.apache.maven:apache-maven to v3.10.0 ([#11](https://github.com/quokkify/archgate/issues/11)) ([f018c75](https://github.com/quokkify/archgate/commit/f018c7583a709651f21600475a7c436d7355af9a))


### 🧹 Chores

* **build:** resolve maven-plugin-development from the version catalog ([#9](https://github.com/quokkify/archgate/issues/9)) ([e3f2872](https://github.com/quokkify/archgate/commit/e3f2872457303c813fbe41cf2909c38f90c16e6e))
* **ci-kit:** hand off base, security, release, allure, renovate to modules ([#17](https://github.com/quokkify/archgate/issues/17)) ([0e3bcec](https://github.com/quokkify/archgate/commit/0e3bcec91b9d0a69cd030329c634eadc87ebc204))
* **ci-kit:** hand off validate to core module ([#5](https://github.com/quokkify/archgate/issues/5)) ([c2525a7](https://github.com/quokkify/archgate/commit/c2525a72d45970b2b193f4db765c8b40681fb308))
* initial commit ([65308fe](https://github.com/quokkify/archgate/commit/65308fe3e319d06a98c60390ca56ae180112a1b0))
* release 0.1.0 as the first version ([#14](https://github.com/quokkify/archgate/issues/14)) ([5c3e149](https://github.com/quokkify/archgate/commit/5c3e14933aeadf76afb69ef75612f20a00482e43))
* **template:** update shared project template ([#10](https://github.com/quokkify/archgate/issues/10)) ([003bf93](https://github.com/quokkify/archgate/commit/003bf931aebed7b1ff56f271d2b84a097dcdd841))
* **template:** update shared project template ([#13](https://github.com/quokkify/archgate/issues/13)) ([4a37799](https://github.com/quokkify/archgate/commit/4a377996831f0cb02e7dc543ebaef713a2eea214))
* **template:** update shared project template ([#16](https://github.com/quokkify/archgate/issues/16)) ([6dc69b3](https://github.com/quokkify/archgate/commit/6dc69b3f7579ad6addf5fc0b3c088bc4aebac414))
* **template:** update shared project template ([#18](https://github.com/quokkify/archgate/issues/18)) ([4f740a1](https://github.com/quokkify/archgate/commit/4f740a1236ed4507650089a3253db683c0cec008))
* **template:** update shared project template ([#19](https://github.com/quokkify/archgate/issues/19)) ([ef8c51f](https://github.com/quokkify/archgate/commit/ef8c51fe72edecfc5eb38e9b3959825d44d69164))
* **template:** update shared project template ([#20](https://github.com/quokkify/archgate/issues/20)) ([e787421](https://github.com/quokkify/archgate/commit/e787421ac6626e20b3d919c27248261637cbbc43))
* **template:** update shared project template ([#22](https://github.com/quokkify/archgate/issues/22)) ([563345d](https://github.com/quokkify/archgate/commit/563345d9fe055e40fb7774e74213a8b15dfd35f6))
* **template:** update shared project template ([#3](https://github.com/quokkify/archgate/issues/3)) ([d9407f1](https://github.com/quokkify/archgate/commit/d9407f1f47fa76b230c6b29975fd0ae4bfba4d8e))
* **template:** update shared project template ([#4](https://github.com/quokkify/archgate/issues/4)) ([05a9887](https://github.com/quokkify/archgate/commit/05a9887abe7686fc77eab5c196a7fac1583b50d1))
