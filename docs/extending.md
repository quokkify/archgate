# Writing a backend or rule

This page is the contract between ArchGate and code that plugs into it: rules (`ArchitectureRule`) and backends (`AnalysisContext` with its `ContextProvider`). The README shows [how to write a rule](../README.md#write-your-own-rule); this page lists what a rule or a backend may rely on and what it must guarantee.

## What is public

- `archgate-api` (`dev.quokkify.archgate.api`) is the public contract. Its version is the one a rule library declares in `Archgate-Api-Version` (see [Rule libraries](configuration.md#rule-libraries)).
- The backends `archgate-context-archunit`, `archgate-context-javaparser` and `archgate-context-classgraph` are public for the rules that use them.
- `archgate-core` is internal. Its classes are public only so the Maven and Gradle plugins can call them. They have no compatibility promise and may change in any release, so do not depend on them.

## How a run works

1. Core loads the rules listed in `META-INF/services/dev.quokkify.archgate.api.ArchitectureRule` and the providers listed in `META-INF/services/dev.quokkify.archgate.api.ContextProvider` from the test runtime classpath.
2. Every rule's `verify` is called once, and all rules run concurrently, one virtual thread per rule.
3. The first rule that asks for a context type makes core call that type's provider. Exactly one provider per type must be registered; none or several is an unable-to-verify error. The context is built once and the same instance is passed to every rule of that type, concurrently.
4. After all rules have finished, core closes every built context that implements `AutoCloseable`. A failure to close is an unable-to-verify error.

## Rules

- `verify` must be safe to call on a shared context from several threads at once. Keep per-run state in local variables, not in fields of the rule.
- To report findings, throw `ArchitectureViolation`, usually through `checkViolations(expectedContract, violations)`. An `AssertionError` is reported as a finding too. To list findings by reason, call `checkViolationGroups(expectedContract, groups)` with `ViolationGroup(title, violations)` entries: each non-empty group is printed as `Violations (N) "<title>":`, empty groups are omitted, and an empty line separates consecutive groups. When the findings of a group have the same fields, build it with `ViolationGroup.table(title, columns, rows)`: pass one row of cells per violation and ArchGate aligns the columns under a header row. Every row needs one cell per column; a blank or `null` header, a `null` cell or a line break in a cell is rejected with `IllegalArgumentException`. For a table group, `violations()` holds each row with its cells joined by two spaces. `ArchitectureViolation.count()` returns the number of violations, counting every entry and row; the table report style shows it in the `FOUND` column. The rule's `severity()` decides whether a finding fails the build under the configured `failOn`.
- Throw `UnableToVerifyError` when the rule cannot give a trustworthy answer, for example because the classes it needs are missing. It fails the build whatever `failOn` says, and no report is written. Any other exception or error from `verify` is turned into an unable-to-verify error as well.
- Never pass silently because there was nothing to look at: a rule that checked nothing must throw `UnableToVerifyError` rather than return.
- A rule whose `scopes()` holds more than one scope reads them through its own `scopesToCheck(context)`. By default it returns the scopes whose view has something to analyse, so a module without test classes is still checked in main, and it throws `UnableToVerifyError` when every scope is empty or `scopes()` is empty. A rule that reads one scope reads its view directly, which fails closed when the view is empty.
- To make a rule strict again, so that an empty view in any of its scopes fails the build, override `requireAllScopes()` to return `true`. `scopesToCheck(context)` then returns every scope, and reading the empty view throws `UnableToVerifyError`. For a built-in rule, subclass it and register the subclass:

  ```java
  public class StrictNoThreadSleepRule extends NoThreadSleepRule {
    @Override
    public boolean requireAllScopes() {
      return true;
    }
  }
  ```

  A rule that does not read views overrides `requireAllScopes()` the same way but has to honour it itself, as `ServiceRegistrationRule` does.
- After `verify` has returned or reported a finding, core calls `skippedScopes(context)` and adds the result to the rule's report line, for example `No Thread.sleep (TEST not checked: empty)`. The default lists the rule's scopes for which `context.isEmpty(scope)` is true, which is right for a rule that uses `scopesToCheck(context)`. It must return a subset of `scopes()` and never `null`; anything else fails the run as unable to verify, naming the rule. Each rule with a skipped scope is also counted in the verdict line, for example `Gate PASSED: 7 rules (2 partially checked), no findings`. Override it only when the rule decides what to read in another way, for example from the class directories in `layout()`.
- A rule that needs variables or a service reads them from `context.environment()`; see [Rules that need variables or a service](#rules-that-need-variables-or-a-service).
- A rule needs a public no-arg constructor and must be listed in the services file. A rule class in the gate module that is not listed fails the run with a registration error (see [Registration](configuration.md#registration)).

## Rules that need variables or a service

Every built-in context has `environment()`. Use it instead of `System.getenv`, so the build tool's `-DNAME=value` is honoured and the gate can skip the rule where that is allowed:

```java
@Override
public void verify(ArchUnitContext context) {
  Environment env = context.environment();
  Map<String, String> secrets = env.require("TRACKER_URL", "TRACKER_TOKEN");
  Optional<String> changed = env.optional("archgate.changedFiles");
  try {
    check(secrets.get("TRACKER_URL"), secrets.get("TRACKER_TOKEN"), changed);
  } catch (IOException e) {
    throw env.unavailable("Tracker", e);
  }
}
```

- `require(names...)` returns the values. If any is unset or blank it throws `ExternalDependencyUnavailable` naming all of them. After validating the names (a null or blank one throws `IllegalArgumentException`), it throws the same signal when `archgate.offline=true` switches external rules off outside CI.
- `optional(name)` returns an empty value for an unset or blank variable. It never skips the rule, and `archgate.offline` does not apply to it.
- `unavailable(service, cause)` creates the signal for a service that does not answer. `throw` it; it is not thrown for you.
- `ExternalDependencyUnavailable` is the only signal that makes a rule `[SKIP]`. Outside CI the rule is skipped; in CI the build fails, except that `archgate.ci.serviceUnavailable=skip` skips a service failure. A finding is still an `ArchitectureViolation`. See [Rules that call external services](configuration.md#rules-that-call-external-services).
- A third-party `AnalysisContext` or `ProjectLayout` gets an actionable `UnableToVerifyError` when a rule calls `environment()` or `services()` until it implements them. A context should delegate `environment()` to `ProjectLayout.services()` on each call, not when it is created: on Gradle, obtaining the environment means the next run cannot be `UP-TO-DATE`.

## Rules that share a load

Several rules sometimes need the same expensive input, for example the answer of an outside service. Ask for it through `context.shared(name, build)`, available on every built-in context:

```java
@Override
public void verify(ArchUnitContext context) {
  Set<String> index = context.shared("issue index", () -> loadIssueIndex(context.environment()));
  checkViolations("Cases refer to existing issues", unknownIssues(index));
}
```

- The value is built once per gate execution. A gate aggregates every selected module, so a multi-module build with one gate module loads it once, not once per module. The first caller builds the value; concurrent callers wait and get the same object, so treat it as read-only.
- If `build` throws, every caller gets the same throwable and `build` is not called again, so a service that is down is asked once. An `unavailable(...)` signal thrown inside `build` therefore skips every rule that uses the value. An `AssertionError` or `ArchitectureViolation` thrown by `build` is cached too and becomes a finding in every rule that uses the name.
- One name is one value: rules that use the same name must pass equivalent inputs, because only the first `build` runs.
- `build` must not ask for its own name. The same thread asking again fails with `UnableToVerifyError`: `Shared value '<name>' is requested again while it is being built, so it can never be built.` Asking for another shared value inside `build` is fine, unless shared values depend on each other in a cycle across rules that run in parallel. That is not detected and the run hangs.
- Time spent inside `shared` is not counted in the durations of the rules. The report prints the build time once per name on a `Shared: <name> <ms> ms, ...` line after `Model build:`, failed builds included.
- A third-party `AnalysisContext` must implement `shared`; the default throws `UnableToVerifyError`. Delegate it to `ProjectLayout.services().shared(name, build)`.

## Backends

- `ContextProvider.type()` names the context type it builds, and `create(ProjectLayout)` builds it. Register the provider in `META-INF/services/dev.quokkify.archgate.api.ContextProvider`.
- The context is shared by concurrently running rules, so it must be thread-safe. Build expensive views lazily and once, for example under a lock or with a memoizing holder.
- `ProjectLayout.classDirs(scope)` may contain processed-resources directories next to class directories (Gradle keeps them apart, Maven does not), and paths that do not exist (a module without main classes). Skip what does not exist; do not fail on it.
- `ProjectLayout.sourceDirs(scope)` contains only existing directories. `ProjectLayout.classLoader()` sees the test runtime classpath of the gate module.
- When a scope has nothing to analyse under `rootPackage`, throw `UnableToVerifyError` with a message that says what is missing, so that rules using the view cannot pass by checking nothing.
- Implement `isEmpty(Scope)` so that rules reading several scopes can skip an empty one: return `true` exactly when reading that view would throw for lack of anything to analyse. It may build the view, so remember the result and build it once. The default returns `false`, so a backend that does not implement it keeps failing closed on every empty view. The built-in backends treat a view as empty when ArchUnit finds no class under `rootPackage`, JavaParser no source under `rootPackage`, and ClassGraph no existing class directory.
- If the context holds resources, implement `AutoCloseable`; core closes it after the run. Do not close it from a rule.
- `buildTimes()` is printed in the `Model build:` line of the report. Core merges the maps of all contexts into one map, and also adds an entry under each context's simple class name, so the keys of all backends share one namespace. Prefix keys with the backend name, for example `javaparser main`, so two backends never overwrite each other's entries.

## Requiring another annotation on test methods

`RequiredTestAnnotationRule` in `archgate-rules-archunit-tms` is an abstract base for rules of the form "every TestNG test method carries annotation X". It is not registrable itself. Extend it in your project, pass a `RequiredAnnotation` and the exclusions, and list your subclass in the services file. [TmsLink required](rules/tms-link-required.md) is built this way.

```java
public class OwnerRequiredRule extends RequiredTestAnnotationRule {
  public OwnerRequiredRule() {
    super(
        RequiredAnnotation.of("io.qameta.allure.Owner").requiringNonBlankValue(),
        Map.of("..setup..", "fixtures, not cases"));
  }
}
```

- `RequiredAnnotation.of(name)` takes the fully qualified name; a nested annotation is written `Outer$Inner`.
- `requiringNonBlankValue()` also rejects a missing, blank or non-`String` `value()`.
- `withContainer(name)` is needed for a repeatable annotation, because a method that repeats it carries only the container.
- Exclusion keys are package patterns (with `..` or `*`) or annotation names, as in [TmsLink required](rules/tms-link-required.md).
- The rule is named `<AnnotationSimpleName> on test methods`, has severity **WARNING**, and reads the test classes only. Override `expectedContract()` to change the text shown with the violations.

Annotations are matched by name, so neither the annotation nor TestNG has to be on the rule's compile classpath. A project annotation such as `com.example.Smoke` works the same way.

## Adding a test-management backend

The rules that check `@TmsLink` case ids against a test-management system are split in two. `archgate-cases-archunit` (`dev.quokkify.archgate.cases.archunit`) holds everything that does not depend on the system: finding the linked ids in the test classes, the changed-files mode, the shared load, the grouping and wording of the findings, and the mapping of failures to `[SKIP]` or unable to verify. A backend module adds only the part that talks to its system: one `CaseService` and two small rule classes. [TestRail](rules/testrail-case-exists.md) (`archgate-rules-archunit-testrail`) and [TestPlanIt](rules/testplanit-case-exists.md) (`archgate-rules-archunit-testplanit`) are built this way and are the examples to copy.

**1. Implement `CaseService`.** It must be thread-safe and must not log.

```java
final class TrackerCaseService implements CaseService {

  @Override
  public String displayName() {
    return "Tracker";                      // rule names, findings, "Tracker index", skip messages
  }

  @Override
  public List<String> requiredVariables() {
    return List.of("TRACKER_URL", "TRACKER_TOKEN", "TRACKER_PROJECT_ID");
  }

  @Override
  public CaseIndex load(CaseQuery query, Map<String, String> variables) throws CaseServiceUnavailableException {
    // look up query.ids() and place every one of them in exactly one category
    return new CaseIndex(existing, missing, deleted, foreign);
  }
}
```

- `requiredVariables()` are read only when some test links a case, through `require`, so missing ones skip the rules outside CI and fail them in CI; see [Rules that call external services](configuration.md#rules-that-call-external-services). `load` receives their values.
- `load` is called at most once per run and mode, with the sorted, positive linked ids in `query.ids()`. `query.changedFilesOnly()` tells that only changed test classes were scanned, so the ids are usually few.
- The returned `CaseIndex` must place every queried id in one category: `existing` (with its `AutomationType`), `missing`, `deleted`, or `foreign` (with a label of the other project, such as its id). An unclassified id or an id in two categories fails the run as unable to verify.
- `AutomationType.named(name)` for a type the system names, `notSet()` for a case without one, `unknown(raw)` for a raw value without a name, and `unavailable(reason)` when the system's automation types cannot be read at all, such as a missing field: the exists rule ignores it and the automated rule fails as unable to verify with the reason. A system with a plain automated flag maps it to two fixed names, as TestPlanIt does with `Automated` and `Manual`.
- Throw `CaseServiceUnavailableException` when the system does not answer, answers with an error or answers with something unreadable; the rules are then skipped outside CI and fail in CI unless `archgate.ci.serviceUnavailable=skip`. Throw `UnableToVerifyError` with an actionable message for refused credentials and for settings that cannot work: those fail the run everywhere. Any other exception, and an `AssertionError`, also becomes unable to verify, never a finding.
- Never echo a variable's value in a message: a secret may sit in the wrong variable. Name the variable instead.
- Do not trust an empty answer on its own. If the system answers a request it does not authenticate with no data instead of an error, check access first; the TestPlanIt backend reads the configured project before any case for this reason.

**2. Override `shareKey()` when the service has settings of its own.** Both rules of a run share one load, named `<displayName> index` (`<displayName> index (changed files)` in changed-files mode). They reuse it only when the service class, the share key, the variable values and the query are equal; otherwise the run fails as unable to verify. The default share key is the service class, which suits a service without settings, like both built-in backends. A service that takes settings, for example through a subclass constructor, returns a value with `equals` and `hashCode` (a record works well) holding every setting that affects `load`. The variables need not be part of it, and it is never printed.

**3. Extend the two rule base classes** with public no-arg constructors, so they can be registered:

```java
public class TrackerCaseExistsRule extends CaseExistsRule {
  public TrackerCaseExistsRule() {
    super(new TrackerCaseService());
  }
}

public class TrackerCaseAutomatedRule extends CaseAutomatedRule {
  public TrackerCaseAutomatedRule() {
    this(Set.of("Automated"), Set.of("Manual"));
  }

  protected TrackerCaseAutomatedRule(Set<String> automated, Set<String> manual) {
    super(new TrackerCaseService(), automated, manual);
  }
}
```

- `CaseExistsRule` reports missing, deleted and foreign ids in fixed groups; it is named `<displayName> case exists`.
- `CaseAutomatedRule` reports existing cases whose type is neither in the automated nor in the manual set; it is named `<displayName> case automated`. Cases of a manual type are never reported, so leave the manual set empty when every non-automated case should be reported, as the TestPlanIt rule does by default. A protected constructor with both sets lets users name their own types.
- Both rules have severity INFO and read the test classes only.

**4. Package it** as a `rules-archunit-<system>` module on `cases-archunit` with no services file, and add it to `settings.gradle`, the BOM, rule pages and README rows; see the module map in `AGENTS.md`.
