# No console output

`dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule` · **ERROR** · main

Production code must not write to the console. The rule forbids:
- `System.out`
- `System.err`
- `Throwable.printStackTrace()`
- `Thread.dumpStack()`

**Why.** Console output skips the logging configuration, so it has no levels, no format and no routing.

```java
// ✗ violation
System.out.println("Created user " + id);
e.printStackTrace();

// ✓ fix
private static final Logger LOG = LoggerFactory.getLogger(UserSteps.class);
LOG.info("Created user {}", id);
LOG.error("User creation failed", e);
```

**Register**

```
dev.quokkify.archgate.rules.archunit.NoConsoleOutputRule
```

**Configure.** This rule has no parameters. To change its severity, subclass it and override `severity()`.

[← All rules](../../README.md#built-in-rules)
