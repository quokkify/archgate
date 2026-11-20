# TestRail case automated

`dev.quokkify.archgate.rules.archunit.testrail.TestRailCaseAutomatedRule` · **INFO** · test · artifact `archgate-rules-archunit-testrail`

Every existing TestRail case linked through `@TmsLink` from a TestNG test method must have an automated automation type, read from the case field `custom_automation_type`. By default the type `Automated` counts as automated and cases of type `Manual` are never reported; every other type, a case without a type and an option id the field does not list are reported. Ids without a live case in the project are left to [TestRail case exists](testrail-case-exists.md).

**Why.** A test that runs automatically while its case still says "to automate", or has no type at all, makes the coverage figures in TestRail wrong.

```
[INFO]  TEST         904 ms  TestRail case automated
    Every @io.qameta.allure.TmsLink case id on a test method must identify a TestRail case whose automation type is an automated one; manual cases are not reported.
    Violations (3) "unexpected automation type":
        - C3  OrderTest.java:12 (actual 'To automate', expected 'Automated')
        - C4  OrderTest.java:16 (actual not set, expected 'Automated')
        - C5  OrderTest.java:20 (actual unknown (99), expected 'Automated')
```

`unknown (99)` means the case stores an option id that the field definition does not list.

**Automation field.** The types are the options of a dropdown case field, by default the one with system name `custom_automation_type`. It is a custom field, so your TestRail may not have it or may call it something else. When TestRail lists no field of that name, or its options are not lines of the form `<id>, <name>`, this rule fails as unable to verify, wherever it runs, with a message that names the field; this is a configuration problem, not an outage, so it is never skipped. [TestRail case exists](testrail-case-exists.md) is not affected and still checks the links. Only the field's first configuration is read.

**Variables, skips and load.** The same as for [TestRail case exists](testrail-case-exists.md): the four `TESTRAIL_*` variables, `[SKIP]` outside CI and a failed build in CI when they are missing or TestRail does not answer (see [Rules that call external services](../configuration.md#rules-that-call-external-services)), one shared `TestRail index` for both rules, and only the changed test classes with `archgate.changedFiles` (see [Checking only the files a pull request changed](../configuration.md#checking-only-the-files-a-pull-request-changed)).

**Register**

```
dev.quokkify.archgate.rules.archunit.testrail.TestRailCaseAutomatedRule
```

**Configure.** Subclass the rule to name the automation types of your TestRail instance, then register the subclass instead:

```java
public class ProjectCaseAutomatedRule extends TestRailCaseAutomatedRule {
  public ProjectCaseAutomatedRule() {
    super(Set.of("Automated", "Automated API"), Set.of("Manual"));
  }
}
```

The first set must not be empty, and a name may not be in both sets.

If your automation types live in another field, pass its system name as a third argument, and subclass [TestRail case exists](testrail-case-exists.md) with the same name, because both rules share one load; rules that name different fields fail as unable to verify with `differing service configuration`:

```java
public class ProjectCaseAutomatedRule extends TestRailCaseAutomatedRule {
  public ProjectCaseAutomatedRule() {
    super(Set.of("Automated"), Set.of("Manual"), "custom_test_kind");
  }
}

public class ProjectCaseExistsRule extends TestRailCaseExistsRule {
  public ProjectCaseExistsRule() {
    super("custom_test_kind");
  }
}
```

[← All rules](../../README.md#built-in-rules)
