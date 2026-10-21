# Service registration

English · [Русский](service-registration.ru.md)

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

**Register**

```
dev.quokkify.archgate.rules.archunit.ServiceRegistrationRule
```

**Configure.** This rule has no parameters. To change its severity, subclass it and override `severity()`.

[← All rules](../../README.md#built-in-rules)
