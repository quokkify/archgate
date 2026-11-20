# TestRail case exists

`dev.quokkify.archgate.rules.archunit.testrail.TestRailCaseExistsRule` · **INFO** · test · artifact `archgate-rules-archunit-testrail`

Every `@TmsLink` case id on a TestNG test method, such as `C123` or `123`, must name a case of the TestRail project in `TESTRAIL_PROJECT_ID` that is not deleted. Values inside `@TmsLinks` count too. Values that are not case ids are left to [TmsLink format](tms-link-format.md).

**Why.** A link to a case that does not exist, was deleted or lives in another project sends results nowhere, or to the wrong project.

```
[INFO]  TEST         912 ms  TestRail case exists
    Every @io.qameta.allure.TmsLink case id on a test method must identify an existing TestRail case.
    Violations (1) "case does not exist in TestRail":
        - C9        PaymentTest.java:14
    Violations (1) "case is deleted in TestRail":
        - C10       PaymentTest.java:18
    Violations (1) "case belongs to another TestRail project":
        - C1234567  PaymentTest.java:22 (project 3)
```

The groups always come in this order, and empty ones are left out. Each line shows the first method that links the id and ` (+N more)` when several do. The foreign project is shown by its id.

**Variables.** Read only when some test links a case, and then all four are required; none has a default:

| Variable | Value |
|---|---|
| `TESTRAIL_URL` | The TestRail address, for example `https://tms.example.org`; http or https, no user info, query or fragment. Requests go to `<url>/index.php?/api/v2/...` |
| `TESTRAIL_USER` | The user name |
| `TESTRAIL_PASSWORD` | The password or an API key of that user |
| `TESTRAIL_PROJECT_ID` | The numeric id of the project the links must point into |

Pass them as `-DNAME=value` or as environment variables, see [Where variables come from](../configuration.md#where-variables-come-from). No message ever repeats a value.

**Skipped or failed.** What happens when the rule cannot get an answer follows [Rules that call external services](../configuration.md#rules-that-call-external-services):

| Situation | Outside CI | In CI |
|---|---|---|
| `TESTRAIL_URL`, `TESTRAIL_USER`, `TESTRAIL_PASSWORD`, `TESTRAIL_PROJECT_ID` not all set | `[SKIP]` naming the missing ones | The build fails |
| `archgate.offline=true` | `[SKIP]` | Ignored, the rule runs |
| TestRail does not answer, times out, answers with an error status (including rate limiting, HTTP 429) or with something unreadable | `[SKIP]` | The build fails, or `[SKIP]` with `archgate.ci.serviceUnavailable=skip` |
| A URL or project id that cannot be used, or credentials TestRail refuses (HTTP 401 or 403) | The build fails as unable to verify, wherever it runs | Same |

The load also reads the case field that holds the automation type, `custom_automation_type` by default. This rule does not need it: when the field is missing or its options cannot be read, it still checks every link, and only [TestRail case automated](testrail-case-automated.md) fails as unable to verify.

A skip looks like this in the report:

```
[SKIP]  TEST           0 ms  TestRail case exists
    Missing TESTRAIL_USER, TESTRAIL_PASSWORD. Pass them as -DNAME=value or as environment variables.
    Skipped because this is not a CI run (no CI variable set); in CI this fails the build.
```

**One load per run.** This rule and [TestRail case automated](testrail-case-automated.md) share one load of the linked cases, named `TestRail index`, so TestRail is asked once per gate execution, also in a multi-module build. The report shows it on the `Shared:` line, for example `Shared: TestRail index 840 ms`. The load reads the project's suites and looks the linked cases up, one by one while there are few and by paging whole suites when there are many, with 8 requests in flight and 60 s for the whole load. A failed load is not repeated: both rules get the same skip or failure. When a project links no case at all, neither rule reads a variable or makes a request, and both pass.

**Changed files.** With `archgate.changedFiles` set, only the test classes of the listed source files are checked and only their ids are looked up; the load is then named `TestRail index (changed files)`. When none of the listed files is a test class with a link, no request is made. A file that cannot be read fails the run. See [Checking only the files a pull request changed](../configuration.md#checking-only-the-files-a-pull-request-changed).

**Register**

```
dev.quokkify.archgate.rules.archunit.testrail.TestRailCaseExistsRule
```

**Configure.** When the automated rule reads another automation field, subclass this rule with the same field name, `super("custom_test_kind")`, and register the subclass, so both rules share one load; see [TestRail case automated](testrail-case-automated.md).

[← All rules](../../README.md#built-in-rules)
