# hashCode and equals together

`dev.quokkify.archgate.rules.archunit.HashCodeEqualsTogetherRule` · **ERROR** · main

A class that overrides one of `equals` or `hashCode` must override both.

**Why.** Overriding only one breaks the contract of hash-based collections: equal objects can land in different buckets, `HashSet.contains` returns wrong answers, and `HashMap` lookups lose entries.

```java
// ✗ violation
public class UserId {
  private final String id;

  @Override
  public boolean equals(Object other) { /* ... */ }
  // hashCode is missing
}

// ✓ fix
public class UserId {
  private final String id;

  @Override
  public boolean equals(Object other) { /* ... */ }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
```

**Register**

```
dev.quokkify.archgate.rules.archunit.HashCodeEqualsTogetherRule
```

**Configure.** This rule has no parameters. To lower its severity, subclass it and register the subclass:

```java
public class SoftHashCodeEqualsRule extends HashCodeEqualsTogetherRule {
  @Override
  public Severity severity() {
    return Severity.WARNING;
  }
}
```

[← All rules](../../README.md#built-in-rules)
