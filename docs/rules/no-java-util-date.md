# No java.util.Date

`dev.quokkify.archgate.rules.archunit.NoJavaUtilDateRule` · **WARNING** · main + test

Code must not depend on `java.util.Date`. Use `java.time` types instead: `Instant`, `LocalDateTime` or `ZonedDateTime`.

**Why.** `Date` is mutable and has no time zone.

```java
// ✗ violation
Date expiry = new Date(System.currentTimeMillis() + 3_600_000);

// ✓ fix
Instant expiry = Instant.now().plus(Duration.ofHours(1));
```

**Register**

```
dev.quokkify.archgate.rules.archunit.NoJavaUtilDateRule
```

**Configure.** Some APIs still require a `Date`, such as Selenium's `Cookie`. Allow the classes that bridge to such APIs. The key is the fully qualified class name, the value is the reason:

```java
public class AcmeNoJavaUtilDateRule extends NoJavaUtilDateRule {
  public AcmeNoJavaUtilDateRule() {
    super(Map.of("com.acme.tests.ui.CookieFactory", "Selenium Cookie API takes java.util.Date"));
  }
}
```

Register `com.acme.tests.arch.AcmeNoJavaUtilDateRule` instead of the base rule.

[← All rules](../../README.md#built-in-rules)
