# Test assertions

`dev.quokkify.archgate.rules.archunit.TestAssertionsRule` · **WARNING** · test

Every test method must assert or verify something through one of the assertion APIs. A test without an assertion runs the code but checks nothing.

**Why.** A test that only calls the production code is green even when the behavior is broken. It inflates the coverage numbers while guarding nothing.

```java
// ✗ violation
@Test
public void loginWorks() {
  page.login(USER); // runs, never checks
}

// ✓ fix
@Test
public void loginWorks() {
  page.login(USER);
  assertEquals("Welcome", page.header());
}
```

The rule recognizes calls owned by:

- `org.testng.Assert`, `org.testng.asserts.Assertion`, `org.testng.asserts.SoftAssert`,
- `org.assertj.core.api.Assertions`,
- `org.junit.jupiter.api.Assertions`.

**Register**

```
dev.quokkify.archgate.rules.archunit.TestAssertionsRule
```

**Configure.** Both the test annotations that select test methods and the assertion-API owner classes are configurable — subclass the rule and pass the fully qualified names:

```java
public class MyTestAssertionsRule extends TestAssertionsRule {
  public MyTestAssertionsRule() {
    super(Set.of("my.framework.Test"), Set.of("my.framework.Assertions"));
  }
}
```

[← All rules](../../README.md#built-in-rules)
