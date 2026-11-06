# No Thread.sleep

`dev.quokkify.archgate.rules.archunit.NoThreadSleepRule` · **ERROR** · main + test

Code must not call `Thread.sleep` or `TimeUnit.sleep`. Wait for the condition you need instead.

**Why.** A fixed delay is either too short, which makes tests flaky, or too long, which makes them slow.

```java
// ✗ violation
Thread.sleep(2000);
assertThat(page.status()).isEqualTo("DONE");

// ✓ fix
await().atMost(Duration.ofSeconds(10)).until(() -> page.status().equals("DONE"));
```

**Empty scope.** Main and test are checked separately. A module without test classes is still checked in main, and one without main classes in test; the report line then names the scope that was not checked, for example `No Thread.sleep (TEST not checked: empty)`. If neither scope has a class under `rootPackage`, the rule fails the build as unable to verify. To fail on any empty scope instead, subclass the rule and override `requireAllScopes()` to return `true` (see [Partial checks](../configuration.md#partial-checks)).

**Register**

```
dev.quokkify.archgate.rules.archunit.NoThreadSleepRule
```

**Configure.** This rule has no parameters. To lower its severity, subclass it and register the subclass:

```java
public class SoftNoThreadSleepRule extends NoThreadSleepRule {
  @Override
  public Severity severity() {
    return Severity.WARNING;
  }
}
```

[← All rules](../../README.md#built-in-rules)
