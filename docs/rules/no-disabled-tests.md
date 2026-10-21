# No disabled tests

English · [Русский](no-disabled-tests.ru.md)

`dev.quokkify.archgate.rules.archunit.NoDisabledTestsRule` · **ERROR** · test

Tests must not be disabled: neither `@Ignore` (TestNG) / `@Disabled` (JUnit 5) on a class or method, nor `@Test(enabled = false)` (TestNG). A disabled test guards nothing while it still looks like coverage — remove it if it is obsolete, or fix and re-enable it.

**Why.** A disabled test is invisible in the reports, but it still pretends to cover the behavior it checks. Coverage numbers stay green while the checked path can silently break.

```java
// ✗ violation
@Ignore("flaky")
public class LoginTest {

  @Test(enabled = false)
  public void loginRejectsWrongPassword() {
  }
}

// ✓ fix
public class LoginTest {

  @Test
  public void loginRejectsWrongPassword() {
  }
}
```

**Register**

```
dev.quokkify.archgate.rules.archunit.NoDisabledTestsRule
```

**Configure.** The rule recognizes TestNG (`org.testng.annotations.Test`, `org.testng.annotations.Ignore`) and JUnit 5 (`org.junit.jupiter.api.Test`, `org.junit.jupiter.api.Disabled`) out of the box. To check other frameworks, subclass the rule and pass the fully qualified annotation names:

```java
public class MyNoDisabledTestsRule extends NoDisabledTestsRule {
  public MyNoDisabledTestsRule() {
    super(Set.of("my.framework.Test"), Set.of("my.framework.Disable"));
  }
}
```

[← All rules](../../README.md#built-in-rules)
