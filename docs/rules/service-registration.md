# Service registration

`dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule` · **ERROR** · main + test

The rule checks every `META-INF/services/*` provider file in the checked modules except ArchGate's own rule list (see below). Each listed class must:
- exist;
- implement the service interface named by the file;
- be public and concrete;
- have a public no-arg constructor.

**Why.** `ServiceLoader` reports a broken provider file only at run time, and often only for the first bad line.

```
# ✗ violation — META-INF/services/org.testng.ITestNGListener
com.acme.tests.listeners.RetryListner        ← typo, class not found
com.acme.tests.listeners.AbstractListener    ← abstract

# ✓ fix
com.acme.tests.listeners.RetryListener
```

**ArchGate's own services file.** A file named exactly `dev.quokkify.archgate.api.ArchitectureRule` counts as a found provider file, but its lines are not checked. The gate that runs in the module holding the file already loads every listed rule and fails on a line it cannot load. Another gate that checks the same module may not have that module's test classpath, for example a rule library that only that module depends on, and would report the module's rules as `class not found`. Skipping the file lets two gates run in one build.

The trade-off: if no gate runs in a module that holds an ArchGate services file, nobody checks that file. That happens with a stale file left in a module without a gate, a gate skipped with `skip`, or a module that only another gate checks.

**Empty scope.** A scope whose class directories do not exist, for example a module without tests, is skipped, and the report line names it, for example `Service registration (TEST not checked: empty)`. If no provider file is found in any class directory, the rule fails the build as unable to verify. To fail on any empty scope instead, subclass the rule and override `requireAllScopes()` to return `true` (see [Partial checks](../configuration.md#partial-checks)).

**Register**

```
dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule
```

**Configure.** This rule has no parameters. To change its severity, subclass it and override `severity()`.

[← All rules](../../README.md#built-in-rules)
