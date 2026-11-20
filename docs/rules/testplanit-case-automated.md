# TestPlanIt case automated

`dev.quokkify.archgate.rules.archunit.testplanit.TestPlanItCaseAutomatedRule` · **INFO** · test · artifact `archgate-rules-archunit-testplanit`

Every existing TestPlanIt case linked through `@TmsLink` from a TestNG test method must be marked automated. TestPlanIt keeps a plain automated flag on each case; the rule shows a set flag as the type `Automated` and a cleared one as `Manual`, and reports every linked case of type `Manual`. Ids without a live case in the project are left to [TestPlanIt case exists](testplanit-case-exists.md).

**Why.** A test that runs automatically while its case is still marked manual makes the automation figures in TestPlanIt wrong.

```
[INFO]  TEST         402 ms  TestPlanIt case automated
    Every @io.qameta.allure.TmsLink case id on a test method must identify a TestPlanIt case whose automation type is an automated one.
    Violations (1) "unexpected automation type":
        - C2  OrderTest.java:12 (actual 'Manual', expected 'Automated')
```

**Variables, skips and load.** The same as for [TestPlanIt case exists](testplanit-case-exists.md): the three `TESTPLANIT_*` variables (the token's user needs read access to the project's test-case repository), `[SKIP]` outside CI and a failed build in CI when they are missing or TestPlanIt does not answer (see [Rules that call external services](../configuration.md#rules-that-call-external-services)), one shared `TestPlanIt index` for both rules, and only the changed test classes with `archgate.changedFiles` (see [Checking only the files a pull request changed](../configuration.md#checking-only-the-files-a-pull-request-changed)).

**Register**

```
dev.quokkify.archgate.rules.archunit.testplanit.TestPlanItCaseAutomatedRule
```

**Configure.** To accept manual cases, which leaves the rule nothing to report on a system with a plain flag, subclass it and pass `Manual` as a manual type:

```java
public class ProjectCaseAutomatedRule extends TestPlanItCaseAutomatedRule {
  public ProjectCaseAutomatedRule() {
    super(Set.of(AUTOMATED), Set.of(MANUAL));
  }
}
```

**Live test.** The module has an opt-in test that runs both rules against a real TestPlanIt in Docker; see [Live TestPlanIt test](../development.md#live-testplanit-test).

[← All rules](../../README.md#built-in-rules)
