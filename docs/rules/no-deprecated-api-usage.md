# No deprecated API usage

English · [Русский](no-deprecated-api-usage.ru.md)

`dev.quokkify.archgate.rules.archunit.NoDeprecatedApiUsageRule` · **WARNING** · main + test

Code must not use anything annotated `@Deprecated` — fields, methods, constructors, superclasses, interfaces or the deprecated classes themselves. The next dependency upgrade can remove them; replace the usage with the supported alternative from the `@Deprecated` documentation.

**Why.** Deprecated APIs work today and break on the next upgrade. Finding every usage now, as a warning in the build, is cheaper than a broken build the night before a release.

```java
// ✗ violation
public String token() {
  return LegacyClient.OLD_FIELD;   // deprecated field
}

// ✓ fix
public String token() {
  return CurrentClient.tokenField();
}
```

The rule reports:

- access to a deprecated field,
- calls to a deprecated method — including methods returning a deprecated type or taking deprecated parameters,
- calls to a deprecated constructor,
- extending a deprecated class or implementing a deprecated interface.

**Register**

```
dev.quokkify.archgate.rules.archunit.NoDeprecatedApiUsageRule
```

**Configure.** This rule has no parameters. To raise its severity, subclass it and register the subclass:

```java
public class StrictNoDeprecatedApiUsageRule extends NoDeprecatedApiUsageRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

[← All rules](../../README.md#built-in-rules)
