# 0008. Rules over several scopes check the non-empty ones

Status: accepted

## Context

[ADR 0003](0003-backend-neutral-api.md) made an empty model for a requested scope an unable-to-verify condition. Every built-in rule over main and test read both views, so a module with main classes and no test classes could not run those rules at all, although the main half could be checked. Consumers with such modules had to drop the rule or copy it as a main-only rule (issue #43).

The empty-view error exists so that a rule never passes by checking nothing. A rule that checks main and skips an empty test view still checks something, so the error is not needed there, as long as the skip is visible and the all-empty case still fails.

Two shapes were possible: an opt-in switch, per rule or in the build file, or a default that applies to every rule over several scopes. A build-file switch breaks the rule that rules are configured by subclassing, not by build settings, and a per-rule switch would have to be set on every built-in rule to fix the reported case.

## Decision

- `AnalysisContext` gets `isEmpty(Scope)`, true when reading the view of that scope would fail for lack of anything to analyse. The default is `false`, so a backend that does not implement it keeps failing closed on every empty view.
- `AnalysisContext.scopesToCheck(Set<Scope>)` returns the non-empty scopes in `Scope` order and throws `UnableToVerifyError` when every given scope is empty. Rules over several scopes read their views through it. A rule over one scope reads its view directly, so an empty view fails as before.
- `ArchitectureRule.requireAllScopes()` (default `false`) and `ArchitectureRule.scopesToCheck(C)`. Rules read their views through `scopesToCheck(context)`, which delegates to the context unless `requireAllScopes()` is `true`; then it returns every scope, so an empty view fails closed as before ADR 0008. A consumer restores the strict behaviour for a built-in rule by subclassing it, in line with configuring rules by subclassing, not by build settings. `ServiceFileConsistencyRule` is `final` and has to be copied instead.
- `ArchitectureRule.skippedScopes(C)` returns the scopes of the rule that were not checked. The default asks `isEmpty` for each scope of `scopes()`; a rule that chooses its input in another way overrides it.
- Core calls `skippedScopes` after a rule has passed or reported a finding and appends the skipped scopes to its report line, for example `No Thread.sleep (TEST not checked: empty)`. A failure while asking for them, `null`, a `null` element or a scope outside `scopes()` is an unable-to-verify error naming the rule. The verdict line counts the rules with a skipped scope, for example `Gate PASSED: 7 rules (2 partially checked), no findings`, so a partial run is visible without reading every line.
- This is the default for every built-in rule over main and test: ArchUnit (`NoThreadSleepRule`, `NoDeprecatedApiUsageRule`, `MaxMethodParametersRule`, `NoJavaUtilDateRule`, and `ServiceRegistrationRule` through its own override), JavaParser (`NoWildcardImportsRule`) and ClassGraph (`ServiceFileConsistencyRule`).

## Consequences

- A module without tests, or a gate whose test view has no class under `rootPackage`, is checked in main instead of failing the build. The report says which scope was not checked, so a partial check is never mistaken for a full one.
- When every scope of a rule is empty, the build still fails as unable to verify. The fail-closed contract holds for each rule.
- A built-in rule whose `scopes()` is narrowed by a subclass now reads only the narrowed scopes. Before, it read both views whatever `scopes()` said.
- The ArchUnit and JavaParser contexts remember an empty view instead of rebuilding it on every call; reading it still throws each time.
- Building without test classes, for example Gradle `-x compileTestJava`, now passes main + test rules on main alone; the report lines and the verdict count show it. Maven's `-Dmaven.test.skip=true` also skips test resources, so a clean build fails with no rules registered instead.
- `archgate-api` gains five default methods, so existing rules and backends compile unchanged. Core now asks every rule for its skipped scopes: on a built-in context this may build a view the rule did not read, once per run, and a rule over several scopes that never read an empty view gets the skip on its report line.
