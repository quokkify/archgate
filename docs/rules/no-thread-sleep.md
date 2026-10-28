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
