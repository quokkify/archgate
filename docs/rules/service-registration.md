# Service registration

`dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule` · **ERROR** · main + test

The rule checks every `META-INF/services/*` provider file in the checked modules, not only the ArchGate one. Each listed class must:
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

**Empty scope.** A scope whose class directories do not exist, for example a module without tests, is skipped, and the report line names it, for example `Service registration (TEST not checked: empty)`. If no provider file is found in any class directory, the rule fails the build as unable to verify.

**Register**

```
dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule
```

**Configure.** This rule has no parameters. To change its severity, subclass it and override `severity()`.

[← All rules](../../README.md#built-in-rules)
