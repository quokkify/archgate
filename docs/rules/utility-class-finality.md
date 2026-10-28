# Utility class finality

`dev.quokkify.archgate.rules.archunit.UtilityClassFinalityRule` · **WARNING** · main

A class whose methods are all static must be `final` and have a private constructor.

**Why.** A utility class is a namespace for functions, not a type to extend or instantiate. Without `final` and a private constructor nothing stops a subclass or an instance that misuses the stateless helpers.

```java
// ✗ violation
public class Strings {
  public static String trim(String value) {
    return value.strip();
  }
}

// ✓ fix
public final class Strings {
  private Strings() {
  }

  public static String trim(String value) {
    return value.strip();
  }
}
```
Classes with at least one instance method, classes with a `main` method, interfaces, enums and abstract classes are not affected.

**Register**

```
dev.quokkify.archgate.rules.archunit.UtilityClassFinalityRule
```

**Configure.** This rule has no parameters. To raise its severity, subclass it and register the subclass:

```java
public class StrictUtilityClassRule extends UtilityClassFinalityRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

[← All rules](../../README.md#built-in-rules)
