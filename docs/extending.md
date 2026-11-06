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
- To report findings, throw `ArchitectureViolation`, usually through `checkViolations(expectedContract, violations)`. An `AssertionError` is reported as a finding too. The rule's `severity()` decides whether a finding fails the build under the configured `failOn`.
- Throw `UnableToVerifyError` when the rule cannot give a trustworthy answer, for example because the classes it needs are missing. It fails the build whatever `failOn` says, and no report is written. Any other exception or error from `verify` is turned into an unable-to-verify error as well.
- Never pass silently because there was nothing to look at: a rule that checked nothing must throw `UnableToVerifyError` rather than return.
- A rule whose `scopes()` holds more than one scope reads them through `context.scopesToCheck(scopes())`. It returns the scopes whose view has something to analyse, so a module without test classes is still checked in main, and it throws `UnableToVerifyError` when every scope is empty. A rule that reads one scope reads its view directly, which fails closed when the view is empty.
- After `verify` has returned or reported a finding, core calls `skippedScopes(context)` and adds the result to the rule's report line, for example `No Thread.sleep (TEST not checked: empty)`. The default lists the rule's scopes for which `context.isEmpty(scope)` is true, which is right for a rule that uses `scopesToCheck`. Override it only when the rule decides what to read in another way, for example from the class directories in `layout()`.
- A rule needs a public no-arg constructor and must be listed in the services file. A rule class in the gate module that is not listed fails the run with a registration error (see [Registration](configuration.md#registration)).

## Backends

- `ContextProvider.type()` names the context type it builds, and `create(ProjectLayout)` builds it. Register the provider in `META-INF/services/dev.quokkify.archgate.api.ContextProvider`.
- The context is shared by concurrently running rules, so it must be thread-safe. Build expensive views lazily and once, for example under a lock or with a memoizing holder.
- `ProjectLayout.classDirs(scope)` may contain processed-resources directories next to class directories (Gradle keeps them apart, Maven does not), and paths that do not exist (a module without main classes). Skip what does not exist; do not fail on it.
- `ProjectLayout.sourceDirs(scope)` contains only existing directories. `ProjectLayout.classLoader()` sees the test runtime classpath of the gate module.
- When a scope has nothing to analyse under `rootPackage`, throw `UnableToVerifyError` with a message that says what is missing, so that rules using the view cannot pass by checking nothing.
- Implement `isEmpty(Scope)` so that rules reading several scopes can skip an empty one: return `true` exactly when reading that view would throw for lack of anything to analyse. It may build the view, so remember the result and build it once. The default returns `false`, so a backend that does not implement it keeps failing closed on every empty view. The built-in backends treat a view as empty when ArchUnit finds no class under `rootPackage`, JavaParser no source under `rootPackage`, and ClassGraph no existing class directory.
- If the context holds resources, implement `AutoCloseable`; core closes it after the run. Do not close it from a rule.
- `buildTimes()` is printed in the `Model build:` line of the report. Core merges the maps of all contexts into one map, and also adds an entry under each context's simple class name, so the keys of all backends share one namespace. Prefix keys with the backend name, for example `javaparser main`, so two backends never overwrite each other's entries.
