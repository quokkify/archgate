package dev.quokkify.archgate.rules.archunit.testplanit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable.Reason;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Runs both rules end to end against a loopback server that answers like the TestPlanIt model API. */
class TestPlanItRulesTest {

    private static final JavaClasses LINKED = new ClassFileImporter().importPackages("archfixtures.testplanitrules");
    private static final JavaClasses UNLINKED = new ClassFileImporter().importPackages("archfixtures.unlinked");
    private static final String PROJECT_CHECK = "projects/findUnique {\"where\":{\"id\":7},\"select\":{\"id\":true}}";
    private static final String CASES_QUERY =
            "repositoryCases/findMany {\"where\":{\"id\":{\"in\":[1,2,9,10,1234567]}},"
                    + "\"select\":{\"id\":true,\"projectId\":true,\"isDeleted\":true,\"automated\":true}}";

    @Test(description = "The exists rule reports missing, deleted and foreign cases in fixed groups")
    void existsRuleReportsEveryKindOfBrokenLink() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("must identify an existing TestPlanIt case")
                    .hasMessageContaining("case does not exist in TestPlanIt")
                    .hasMessageContaining("case is deleted in TestPlanIt")
                    .hasMessageContaining("case belongs to another TestPlanIt project")
                    .hasMessageMatching("(?s).*C9 +PaymentCasesTest\\.java:\\d+.*")
                    .hasMessageMatching("(?s).*C10 +PaymentCasesTest\\.java:\\d+.*")
                    .hasMessageMatching("(?s).*C1234567 +PaymentCasesTest\\.java:\\d+ \\(project 3\\).*")
                    .satisfies(violation ->
                            assertThat(violation.getMessage()).doesNotContainPattern("C[12] +OrderCasesTest"));
        }
    }

    @Test(description = "The automated rule reports linked cases whose automated flag is off")
    void automatedRuleReportsManualCases() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());

            assertThatThrownBy(() -> new TestPlanItCaseAutomatedRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("unexpected automation type")
                    .hasMessageMatching(
                            "(?s).*C2 +OrderCasesTest\\.java:\\d+ \\(actual 'Manual', " + "expected 'Automated'\\).*")
                    .satisfies(violation -> assertThat(violation.getMessage())
                            .doesNotContainPattern("C1 +OrderCasesTest")
                            .doesNotContainPattern("C(9|10|1234567) +PaymentCasesTest"));
        }
    }

    @Test(description = "A subclass that accepts manual cases reports nothing for them")
    void subclassAcceptsManualCases() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());
            TestPlanItCaseAutomatedRule rule = new TestPlanItCaseAutomatedRule(
                    Set.of(TestPlanItCaseAutomatedRule.AUTOMATED), Set.of(TestPlanItCaseAutomatedRule.MANUAL)) {};

            assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
        }
    }

    @Test(description = "Both rules of one run share one load: the project check and one cases query")
    void bothRulesShareOneLoad() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class);
            assertThat(testPlanIt.requests()).containsExactly(PROJECT_CHECK, CASES_QUERY);
            assertThatThrownBy(() -> new TestPlanItCaseAutomatedRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class);

            assertThat(testPlanIt.requests()).containsExactly(PROJECT_CHECK, CASES_QUERY);
        }
    }

    @Test(
            description = "Regression guard: an answer that ignores the token (200 with no data) is never read as "
                    + "'every case is missing'; the project check fails the run as unable to verify")
    void unauthenticatedEmptyAnswerIsUnableToVerify() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            testPlanIt.anonymous();
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageStartingWith("TestPlanIt project 7 not found or the token cannot see it")
                    .hasMessageContaining("TESTPLANIT_TOKEN")
                    .satisfies(TestPlanItRulesTest::assertNoToken);
            assertThat(testPlanIt.requests()).containsExactly(PROJECT_CHECK);
        }
    }

    @Test(description = "A project that does not exist fails the run as unable to verify before any case query")
    void unknownProjectIsUnableToVerify() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            Map<String, String> variables = new HashMap<>(testPlanIt.variables());
            variables.put(TestPlanItCaseService.PROJECT_ID, "8");
            ArchUnitContext context = new FixtureContext(LINKED, variables);

            assertThatThrownBy(() -> new TestPlanItCaseAutomatedRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageStartingWith("TestPlanIt project 8 not found or the token cannot see it");
            assertThat(testPlanIt.requests()).hasSize(1);
        }
    }

    @DataProvider
    Object[][] refusals() {
        return new Object[][] {{401}, {403}};
    }

    @Test(
            dataProvider = "refusals",
            description = "A refused token fails as unable to verify, without the token in any message")
    void refusedTokenIsUnableToVerify(int status) {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            testPlanIt.reply(
                    "projects/findUnique", status, "{\"error\":\"Invalid API token\",\"code\":\"INVALID_TOKEN\"}");
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageStartingWith("TestPlanIt rejected the token (HTTP %d)".formatted(status))
                    .hasMessageContaining("TESTPLANIT_TOKEN")
                    .satisfies(TestPlanItRulesTest::assertNoToken);
        }
    }

    @Test(description = "An unknown token gets 401 from the server and fails as unable to verify")
    void unknownTokenIsUnableToVerify() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            Map<String, String> variables = new HashMap<>(testPlanIt.variables());
            variables.put(TestPlanItCaseService.TOKEN, "tpi_someOtherTokenValue0123456789");
            ArchUnitContext context = new FixtureContext(LINKED, variables);

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageStartingWith("TestPlanIt rejected the token (HTTP 401)")
                    .satisfies(error -> assertThat(error).hasStackTraceContaining("TestPlanIt"))
                    .satisfies(error -> {
                        for (Throwable current = error; Objects.nonNull(current); current = current.getCause()) {
                            assertThat(String.valueOf(current.getMessage())).doesNotContain("someOtherToken");
                        }
                    });
        }
    }

    @DataProvider
    Object[][] unavailableAnswers() {
        return new Object[][] {
            {"repositoryCases/findMany", 500, "{\"error\":\"internal\"}", "returned 500"},
            {"repositoryCases/findMany", 429, "{}", "rate limit"},
            {"repositoryCases/findMany", 302, "", "returned 302"},
            {
                "repositoryCases/findMany",
                422,
                "{\"error\":{\"message\":\"Invalid findMany args\",\"reason\":\"invalid-input\"}}",
                "returned 422"
            },
            {"repositoryCases/findMany", 200, "{\"data\":[{\"id\":1,", "malformed JSON"},
            {"repositoryCases/findMany", 200, "{\"rows\":[]}", "no data member"},
            {"repositoryCases/findMany", 200, "{\"data\":{}}", "not a list"},
            {"repositoryCases/findMany", 200, "{\"data\":[{\"id\":1,\"projectId\":7}]}", "isDeleted"},
            {
                "repositoryCases/findMany",
                200,
                "{\"data\":[{\"id\":5,\"projectId\":7,\"isDeleted\":false,\"automated\":true}]}",
                "case 5 that was not asked for"
            },
            {"projects/findUnique", 500, "{}", "returned 500"},
            {"projects/findUnique", 200, "[]", "no data member"},
        };
    }

    @Test(
            dataProvider = "unavailableAnswers",
            description = "Server errors, redirects and unreadable answers give the service-unavailable signal")
    void badAnswerIsUnavailable(String path, int status, String body, String detail) {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            testPlanIt.reply(path, status, body);
            ArchUnitContext context = new FixtureContext(LINKED, testPlanIt.variables());

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, e -> {
                        assertThat(e.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE);
                        assertThat(e.service()).hasValue("TestPlanIt");
                        assertThat(e).hasMessageContaining(detail);
                    })
                    .satisfies(TestPlanItRulesTest::assertNoToken);
        }
    }

    @Test(description = "An unreachable server gives the service-unavailable signal")
    void unreachableServerIsUnavailable() {
        Map<String, String> variables;
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            variables = testPlanIt.variables();
        }
        ArchUnitContext context = new FixtureContext(LINKED, variables);

        assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                .isInstanceOfSatisfying(
                        ExternalDependencyUnavailable.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE));
    }

    @Test(description = "Without linked cases the rules pass without variables and without requests")
    void noLinksNeedNothing() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            ArchUnitContext context = new FixtureContext(UNLINKED, Map.of());

            assertThatCode(() -> new TestPlanItCaseExistsRule().verify(context)).doesNotThrowAnyException();
            assertThatCode(() -> new TestPlanItCaseAutomatedRule().verify(context))
                    .doesNotThrowAnyException();
            assertThat(testPlanIt.requests()).isEmpty();
        }
    }

    @Test(description = "Missing variables give the skip signal naming the unset ones, before any request")
    void missingVariablesSkip() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            ArchUnitContext context = new FixtureContext(LINKED, Map.of(TestPlanItCaseService.URL, testPlanIt.url()));

            assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                    .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, e -> {
                        assertThat(e.reason()).isEqualTo(Reason.MISSING_VARIABLES);
                        assertThat(e.missingVariables()).containsExactly("TESTPLANIT_TOKEN", "TESTPLANIT_PROJECT_ID");
                    });
            assertThat(testPlanIt.requests()).isEmpty();
        }
    }

    @DataProvider
    Object[][] unusableSettings() {
        String token = FakeTestPlanIt.TOKEN;
        return new Object[][] {
            {
                "ftp://user:" + token + "@tms.example.org",
                token,
                "7",
                "TESTPLANIT_URL is not a usable TestPlanIt address: it must be an absolute http or https URL, "
                        + "for example https://tms.example.org"
            },
            {
                "https://user:" + token + "@tms.example.org",
                token,
                "7",
                "TESTPLANIT_URL is not a usable TestPlanIt address: it must not contain user info, "
                        + "for example https://tms.example.org"
            },
            {
                "https://tms.example.org/?token=" + token,
                token,
                "7",
                "TESTPLANIT_URL is not a usable TestPlanIt address: it must not contain a query or fragment, "
                        + "for example https://tms.example.org"
            },
            {
                "https://tms.example.org",
                token,
                "abc",
                "TESTPLANIT_PROJECT_ID must be the positive numeric id of a " + "TestPlanIt project"
            },
            {
                "https://tms.example.org",
                token,
                "0",
                "TESTPLANIT_PROJECT_ID must be the positive numeric id of a " + "TestPlanIt project"
            },
            {
                "https://tms.example.org",
                token,
                "99999999999",
                "TESTPLANIT_PROJECT_ID must be the positive numeric " + "id of a TestPlanIt project"
            },
            {
                "https://tms.example.org",
                "tpi_with space" + token,
                "7",
                "TESTPLANIT_TOKEN must be a TestPlanIt API token without spaces or control characters"
            },
        };
    }

    @Test(
            dataProvider = "unusableSettings",
            description = "Unusable settings fail as unable to verify, naming the variable but not its value")
    void unusableSettingsAreUnableToVerify(String url, String token, String projectId, String message) {
        ArchUnitContext context = new FixtureContext(
                LINKED,
                Map.of(
                        TestPlanItCaseService.URL, url,
                        TestPlanItCaseService.TOKEN, token,
                        TestPlanItCaseService.PROJECT_ID, projectId));

        assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage(message)
                .satisfies(TestPlanItRulesTest::assertNoToken);
    }

    @Test(description = "Rule metadata")
    void metadata() {
        TestPlanItCaseExistsRule exists = new TestPlanItCaseExistsRule();
        TestPlanItCaseAutomatedRule automated = new TestPlanItCaseAutomatedRule();

        assertThat(exists.name()).isEqualTo("TestPlanIt case exists");
        assertThat(automated.name()).isEqualTo("TestPlanIt case automated");
        for (var rule : List.of(exists, automated)) {
            assertThat(rule.severity()).isEqualTo(Severity.INFO);
            assertThat(rule.scopes()).isEqualTo(EnumSet.of(Scope.TEST));
            assertThat(rule.contextType()).isEqualTo(ArchUnitContext.class);
        }
        TestPlanItCaseService service = new TestPlanItCaseService();
        assertThat(service.displayName()).isEqualTo("TestPlanIt");
        assertThat(service.requiredVariables())
                .containsExactly("TESTPLANIT_URL", "TESTPLANIT_TOKEN", "TESTPLANIT_PROJECT_ID");
    }

    static void assertNoToken(Throwable error) {
        for (Throwable current = error; Objects.nonNull(current); current = current.getCause()) {
            assertThat(String.valueOf(current.getMessage())).doesNotContain(FakeTestPlanIt.TOKEN);
        }
    }
}
