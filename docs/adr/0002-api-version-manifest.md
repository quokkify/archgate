# 0002. API version in the jar manifest

Status: accepted

## Context

A consumer sets the version of the ArchGate plugin independently of the versions of the rule libraries on its test classpath. Inside the engine's class loader the plugin's own `archgate-api` takes precedence, so library rules always run against the plugin's API.

When a rule library was compiled against a newer `archgate-api` than the one running, the failure appears late and misleadingly: a `NoSuchMethodError` wrapped in an unable-to-verify error, with no hint that a version mismatch is the cause.

## Decision

- Every jar that carries rules or the API declares the API version it belongs to in `META-INF/MANIFEST.MF` as `Archgate-Api-Version`. The `archgate-api` jar declares its own version, a rules jar declares the API version it was compiled against.
- The value is `X.Y.Z` or `dev-SNAPSHOT`; anything else is invalid. Releases compare numerically component by component. `dev-SNAPSHOT` is newer than every release and equal to itself. Release builds stamp the release version; every `-SNAPSHOT` build stamps `dev-SNAPSHOT`, the only value outside `X.Y.Z` that the check accepts.
- `RuleRegistry` runs `ApiCompatibilityCheck` before it instantiates any rule. For each registered rule it collects the jar that defines the rule and the jars of its superclasses, so a subclass in the consumer's own classes still checks the built-in rules jar it extends. Each jar is reported once. Jars without the attribute, and classes that live in directories, are not checked.
- A jar that declares a newer or invalid version fails the build with the jar name, both versions and the fix. The check lives in `core`, which has no dependency on any build tool, so both plugins get it unchanged.
- There is no minimum-version policy on the plugin side: a newer plugin keeps working with older libraries while the API stays backward compatible.

## Consequences

- A version mismatch fails before any rule runs, with an actionable message instead of a linkage error.
- Library authors must stamp the attribute from their build. The configuration guide shows the Maven and Gradle snippets.
- Raising the stamped version of a library raises the minimum plugin version of all its consumers, which is why the guide includes a checklist for it.
- Because the check reads manifests, it silently skips what it cannot read (directories, nested jars, missing attribute). A jar without the attribute is trusted.
