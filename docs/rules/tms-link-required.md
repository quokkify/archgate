# TmsLink required

`dev.quokkify.archgate.rules.archunit.tms.TmsLinkRequiredRule` · **WARNING** · test · artifact `archgate-rules-archunit-tms`

Every TestNG test method must carry an Allure `@TmsLink` whose value is not blank. A repeated `@TmsLink` counts through its `@TmsLinks` container. A link on the class or on another method does not satisfy a method that has none.

**Why.** The case id is what ties an automated test to its entry in the test management system. A test without one cannot be traced, reported or retired with its case.

A test method is a method annotated with `org.testng.annotations.Test`, or a public `void` method of a class that has `@Test` on itself or on a superclass, unless it is `public static void main(String[])` or a TestNG configuration, factory or data-provider method. Annotations are matched by name, so the rule needs neither Allure nor TestNG on its own classpath.

```java
// ✗ violation
@Test
public void payOrder() { … }

// ✓ fix
@Test
@TmsLink("C123")
public void payOrder() { … }
```

A violation reads like this:

```
Method <com.example.PaymentTest.payOrder()> is a test without @TmsLink in (PaymentTest.java:12)
Method <com.example.PaymentTest.refund()> has a blank @TmsLink in (PaymentTest.java:20)
```

**Register**

```
dev.quokkify.archgate.rules.archunit.tms.TmsLinkRequiredRule
```

**Configure.** To skip some tests, subclass the rule and pass a map from key to reason. A key with `..` or `*` is an ArchUnit package pattern; any other key is the fully qualified name of an annotation, and a class or method carrying it is skipped:

```java
public class ProjectTmsLinkRequiredRule extends TmsLinkRequiredRule {
  public ProjectTmsLinkRequiredRule() {
    super(Map.of(
        "..setup..", "fixtures, not cases",
        "com.example.SetupOnly", "marks a setup method"));
  }
}
```

An annotation key that no test class or method carries, and a package pattern that matches no test class, are rejected as probable typos, and the rule reports *unable to verify*. To require another annotation, see [`RequiredTestAnnotationRule`](../extending.md#requiring-another-annotation-on-test-methods).

If no test class is found, the rule reports *unable to verify*, which always fails the build.

[← All rules](../../README.md#built-in-rules)
