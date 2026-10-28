# Service file consistency

`dev.quokkify.archgate.rules.classgraph.ServiceFileConsistencyRule` · **ERROR** · main + test · backend: ClassGraph (`archgate-rules-classgraph`)

Every `META-INF/services/<interface>` file in the checked class directories must list all public, concrete classes under `rootPackage` that implement that interface. A class that implements the interface but is missing from the file is reported.

This is the inverse of [Service registration](service-registration.md), which checks that every entry of a services file points to an existing class that implements the interface.

- The rule ignores the `dev.quokkify.archgate.api.ArchitectureRule` services file. Rule registration has its own check.
- Implementers are found transitively: a class that extends a class implementing the interface is reported too.
- Abstract classes, interfaces, enums and non-public classes are not reported.
- If no services file other than the ArchGate one exists in the class directories, there is nothing to compare. The rule reports *unable to verify*, which always fails the build, so a wrong class directory never passes silently.

**Why.** `ServiceLoader` loads only the classes a provider file lists. A provider that was added but never listed is not loaded, and nothing complains until it is missing at run time.

```
# META-INF/services/com.acme.spi.Plugin
com.acme.plugins.AlphaPlugin
```

```java
// ✗ violation: implements Plugin but is not listed
package com.acme.plugins;
public class BetaPlugin implements Plugin { /* ... */ }
```

```
META-INF/services/com.acme.spi.Plugin: com.acme.plugins.BetaPlugin is not listed
```

```
# ✓ fix
com.acme.plugins.AlphaPlugin
com.acme.plugins.BetaPlugin
```

**Register**

```
dev.quokkify.archgate.rules.classgraph.ServiceFileConsistencyRule
```

Add `dev.quokkify.archgate:archgate-rules-classgraph` as a test dependency. It brings `archgate-context-classgraph`.

**Configure.** This rule has no parameters, and the class is `final`, so it cannot be subclassed. To run it at another severity, copy it into your own rule module and change `severity()`.

[← All rules](../../README.md#built-in-rules)
