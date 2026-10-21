# No generic throws

English · [Русский](no-generic-throws.ru.md)

`dev.quokkify.archgate.rules.archunit.NoGenericThrowsRule` · **WARNING** · main

Methods and constructors must not declare the generic exceptions `Exception`, `RuntimeException` or `Throwable`. Declare the specific checked or unchecked exception the code can actually throw.

**Why.** `throws Exception` hides the real failure modes and forces callers into blanket `catch (Exception e)` blocks that swallow unrelated failures. A specific type keeps error handling precise and survives refactoring.

```java
// ✗ violation
public void upload(File file) throws Exception {
}

// ✓ fix
public void upload(File file) throws IOException, InvalidFileException {
}
```

**Register**

```
dev.quokkify.archgate.rules.archunit.NoGenericThrowsRule
```

**Configure.** This rule has no parameters. To lower or raise its severity, subclass it and register the subclass:

```java
public class StrictNoGenericThrowsRule extends NoGenericThrowsRule {
  @Override
  public Severity severity() {
    return Severity.ERROR;
  }
}
```

[← All rules](../../README.md#built-in-rules)
