# Test class naming

English · [Русский](test-class-naming.ru.md)

`dev.quokkify.archgate.rules.archunit.TestClassNamingRule` · **ERROR** · test

A class with `@Test` methods, its own or inherited, must have a name ending in `Test`. Both TestNG and JUnit Jupiter `@Test` (JUnit 5 and 6) are recognised.

**Why.** Suite generation and surefire find test classes by name. A class named `LoginChecks` with `@Test` methods silently never runs.

```java
// ✗ violation
public class LoginChecks { @Test void login() { … } }

// ✓ fix
public class LoginTest { @Test void login() { … } }
```

Abstract base classes are skipped. Put shared code in a class without `@Test` methods.

**Register**

```
dev.quokkify.archgate.rules.archunit.TestClassNamingRule
```

**Configure.** You can set other test annotations and another suffix:

```java
public class AcmeTestClassNamingRule extends TestClassNamingRule {
  public AcmeTestClassNamingRule() {
    super(Set.of("org.testng.annotations.Test"), "IT");
  }
}
```

If no test class is found, the rule reports *unable to verify*, which always fails the build.

[← All rules](../../README.md#built-in-rules)
