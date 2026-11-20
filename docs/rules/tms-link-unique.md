# TmsLink unique

`dev.quokkify.archgate.rules.archunit.tms.TmsLinkUniqueRule` · **WARNING** · test · artifact `archgate-rules-archunit-tms`

A `@TmsLink` case id may appear on one TestNG test method only. Ids come from `@TmsLink` and from every `@TmsLink` inside `@TmsLinks`; surrounding whitespace is stripped before ids are compared and blank values are ignored. One method may carry several different ids.

**Why.** When two methods claim the same case, a result for that case is ambiguous: one failing method hides behind the other's pass.

```java
// ✗ violation
@Test @TmsLink("C123") public void payOrder() { … }
@Test @TmsLink("C123") public void payOrderTwice() { … }

// ✓ fix
@Test @TmsLink("C123") public void payOrder() { … }
@Test @TmsLink("C124") public void payOrderTwice() { … }
```

Each shared id is reported once, listing every method that carries it. Reports are ordered by id, shorter ids first (`C9`, `C10`, `C100`):

```
TmsLink id <C123> is shared by 2 test methods: <com.example.PaymentTest.payOrder()> in (PaymentTest.java:12), <com.example.PaymentTest.payOrderTwice()> in (PaymentTest.java:16)
```

Test methods are recognised as in [TmsLink required](tms-link-required.md).

**Register**

```
dev.quokkify.archgate.rules.archunit.tms.TmsLinkUniqueRule
```

**Configure.** Subclass the rule to leave tests out, with the same map of keys to reasons as [TmsLink required](tms-link-required.md). Excluded tests take no part in the comparison. A case that is deliberately split over several methods of one class can be tolerated:

```java
public class ProjectTmsLinkUniqueRule extends TmsLinkUniqueRule {
  public ProjectTmsLinkUniqueRule() {
    super(Map.of("..setup..", "fixtures, not cases"));
  }

  @Override
  protected boolean allowDuplicatesWithinClass() {
    return true;
  }
}
```

An id repeated within one class and also used in another class is reported either way.

[← All rules](../../README.md#built-in-rules)
