# TmsLink format

`dev.quokkify.archgate.rules.archunit.tms.TmsLinkFormatRule` · **WARNING** · test · artifact `archgate-rules-archunit-tms`

Every non-blank `@TmsLink` value on a TestNG test method must be a case id: a positive number with an optional Latin `C` prefix, such as `123` or `C123`. The default pattern is `TmsLinkFormatRule.DEFAULT_FORMAT`, `C?[1-9]\d*`, and it must match the whole value as written, so `" 123"` is malformed. Each value inside `@TmsLinks` is checked on its own. Blank values are left to [TmsLink required](tms-link-required.md).

**Why.** A value such as `0`, `abc` or `<case_id>` never resolves to a case, so the test is silently unlinked.

```java
// ✗ violations
@TmsLink("0")  @TmsLink("c123")  @TmsLink("12 34")

// ✓ fix
@TmsLink("123")  @TmsLink("C123")
```

```
Method <com.example.PaymentTest.payOrder()> has a malformed @TmsLink "c123" in (PaymentTest.java:12)
```

A Cyrillic `С` looks like the Latin `C` and is a common typo. When the value contains one, the message adds `: Cyrillic 'С' found, use the Latin 'C'`. Test methods are recognised as in [TmsLink required](tms-link-required.md).

**Register**

```
dev.quokkify.archgate.rules.archunit.tms.TmsLinkFormatRule
```

**Configure.** Subclass the rule to skip tests (same map of keys to reasons as [TmsLink required](tms-link-required.md)) or to accept another shape of id:

```java
public class ProjectTmsLinkFormatRule extends TmsLinkFormatRule {
  public ProjectTmsLinkFormatRule() {
    super(Pattern.compile("C?[1-9]\\d*|[A-Z]+-\\d+-[A-Z]"), Map.of());
  }
}
```

The contract line in the report shows the pattern in use; the default one also gives examples.

[← All rules](../../README.md#built-in-rules)
