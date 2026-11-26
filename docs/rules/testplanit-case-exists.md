# TestPlanIt case exists

`dev.quokkify.archgate.rules.archunit.testplanit.TestPlanItCaseExistsRule` · **INFO** · test · artifact `archgate-rules-archunit-testplanit`

Every `@TmsLink` case id on a TestNG test method, such as `C123` or `123`, must name a case of the TestPlanIt project in `TESTPLANIT_PROJECT_ID` that is not deleted. Values inside `@TmsLinks` count too. Values that are not case ids are left to [TmsLink format](tms-link-format.md).

**Why.** A link to a case that does not exist, was deleted or lives in another project sends results nowhere, or to the wrong project.

```
[INFO]  TEST         410 ms  TestPlanIt case exists
    Every @io.qameta.allure.TmsLink case id on a test method must identify an existing TestPlanIt case.
    Violations (1) "case does not exist in TestPlanIt":
        - C9        PaymentTest.java:14

    Violations (1) "case is deleted in TestPlanIt":
        - C10       PaymentTest.java:18

    Violations (1) "case belongs to another TestPlanIt project":
        - C1234567  PaymentTest.java:22 (project 3)
```

The groups always come in this order, and empty ones are left out. Each line shows the first method that links the id and `(+N more)` after it when several do. A soft-deleted case counts as deleted; the foreign project is shown by its id. TestPlanIt shows the token only the cases of projects its user may read, so the foreign group lists only such cases; a case in a project the user cannot read is reported as not existing, so "does not exist" can also mean "not visible to the token".

**Variables.** Read only when some test links a case, and then all three are required; none has a default:

| Variable | Value |
|---|---|
| `TESTPLANIT_URL` | The TestPlanIt address, for example `https://tms.example.org`; http or https, no user info, query or fragment. Requests go to `<url>/api/model/...` |
| `TESTPLANIT_TOKEN` | An API token (`tpi_…`), sent as a Bearer token |
| `TESTPLANIT_PROJECT_ID` | The numeric id of the project the links must point into |

The token's user needs read access to the project and to its test-case repository. TestPlanIt does not answer a read it may not serve with an error but with empty data, so a user without that access would make every case look missing. The rule therefore reads the project first; when the token cannot see it, the run fails with `TestPlanIt project <id> not found or the token cannot see it` instead of reporting the cases.

Pass them as `-DNAME=value` or as environment variables, see [Where variables come from](../configuration.md#where-variables-come-from). No message ever repeats a value.

**Skipped or failed.** What happens when the rule cannot get an answer follows [Rules that call external services](../configuration.md#rules-that-call-external-services):

| Situation | Outside CI | In CI |
|---|---|---|
| `TESTPLANIT_URL`, `TESTPLANIT_TOKEN`, `TESTPLANIT_PROJECT_ID` not all set | `[SKIP]` naming the missing ones | The build fails |
| `archgate.offline=true` | `[SKIP]` | Ignored, the rule runs |
| TestPlanIt does not answer, times out, answers with an error status (including rate limiting, HTTP 429, and redirects) or with something unreadable | `[SKIP]` | The build fails, or `[SKIP]` with `archgate.ci.serviceUnavailable=skip` |
| A URL, token or project id that cannot be used, a token TestPlanIt refuses (HTTP 401 or 403), or a project the token cannot see | The build fails as unable to verify, wherever it runs | Same |

A skip looks like this in the report:

```
[SKIP]  TEST           0 ms  TestPlanIt case exists
    Missing TESTPLANIT_TOKEN, TESTPLANIT_PROJECT_ID. Pass them as -DNAME=value or as environment variables.
    Skipped because this is not a CI run (no CI variable set); in CI this fails the build.
```

**One load per run.** This rule and [TestPlanIt case automated](testplanit-case-automated.md) share one load of the linked cases, named `TestPlanIt index`, so TestPlanIt is asked once per gate execution, also in a multi-module build. The report shows it on the `Shared:` line, for example `Shared: TestPlanIt index 840 ms`. The load reads the project, then the linked cases in chunks of 200 ids, with 30 s per request and 60 s for the whole load. A failed load is not repeated: both rules get the same skip or failure. When a project links no case at all, neither rule reads a variable or makes a request, and both pass.

**Changed files.** With `archgate.changedFiles` set, only the test classes of the listed source files are checked and only their ids are looked up; the load is then named `TestPlanIt index (changed files)`. When none of the listed files is a test class with a link, no request is made. A file that cannot be read fails the run. See [Checking only the files a pull request changed](../configuration.md#checking-only-the-files-a-pull-request-changed).

**Register**

```
dev.quokkify.archgate.rules.archunit.testplanit.TestPlanItCaseExistsRule
```

**Live test.** The module has an opt-in test that runs both rules against a real TestPlanIt in Docker; see [Live TestPlanIt test](../development.md#live-testplanit-test).

[← All rules](../../README.md#built-in-rules)
