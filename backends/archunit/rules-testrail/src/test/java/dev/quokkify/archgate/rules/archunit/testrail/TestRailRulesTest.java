package dev.quokkify.archgate.rules.archunit.testrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable.Reason;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.testng.annotations.Test;

/** Runs both rules end to end against a loopback server that answers like the TestRail API v2. */
class TestRailRulesTest {

    private static final JavaClasses LINKED = new ClassFileImporter().importPackages("archfixtures.testrailrules");
    private static final JavaClasses UNLINKED = new ClassFileImporter().importPackages("archfixtures.unlinked");
    private static final String USER = "reader@example.org";
    private static final String PASSWORD = "s3cret-key";
    private static final String PROJECT = "7";
    private static final int LOAD_REQUESTS = 2 + 1 + 8 + 1;

    @Test(description = "The exists rule reports missing, deleted and foreign cases in fixed groups")
    void existsRuleReportsEveryKindOfBrokenLink() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("must identify an existing TestRail case")
                    .hasMessageContaining("case does not exist in TestRail")
                    .hasMessageContaining("case is deleted in TestRail")
                    .hasMessageContaining("case belongs to another TestRail project")
                    .hasMessageMatching("(?s).*C9 +PaymentCasesTest\\.java:\\d+.*")
                    .hasMessageMatching("(?s).*C10 +PaymentCasesTest\\.java:\\d+.*")
                    .hasMessageMatching("(?s).*C1234567 +PaymentCasesTest\\.java:\\d+ \\(project 3\\).*")
                    .satisfies(violation ->
                            assertThat(violation.getMessage()).doesNotContainPattern("C[1-5] +OrderCasesTest"));
        }
    }

    @Test(description = "The automated rule reports cases to automate, without type and of an unknown type")
    void automatedRuleReportsUnexpectedTypes() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseAutomatedRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("unexpected automation type")
                    .hasMessageMatching("(?s).*C3 +OrderCasesTest\\.java:\\d+ \\(actual 'To automate', "
                            + "expected 'Automated'\\).*")
                    .hasMessageMatching("(?s).*C4 +OrderCasesTest\\.java:\\d+ \\(actual not set, .*")
                    .hasMessageMatching("(?s).*C5 +OrderCasesTest\\.java:\\d+ \\(actual unknown \\(99\\), .*")
                    .satisfies(violation -> assertThat(violation.getMessage())
                            .doesNotContainPattern("C[12] +OrderCasesTest")
                            .doesNotContainPattern("C(9|10|1234567) +PaymentCasesTest"));
        }
    }

    @Test(description = "A subclass names the automation types of its TestRail instance")
    void subclassNamesItsOwnTypes() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());
            TestRailCaseAutomatedRule rule =
                    new TestRailCaseAutomatedRule(Set.of("Automated", "To automate"), Set.of("Manual")) {};

            assertThatThrownBy(() -> rule.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("expected 'Automated or To automate'")
                    .satisfies(violation -> assertThat(violation.getMessage()).doesNotContain("C3 "));
        }
    }

    @Test(description = "Both rules of one run share a single load of the index")
    void bothRulesShareOneLoad() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class);
            int afterFirst = testRail.requests().size();
            assertThatThrownBy(() -> new TestRailCaseAutomatedRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class);

            assertThat(afterFirst).isEqualTo(LOAD_REQUESTS);
            assertThat(testRail.requests()).hasSize(LOAD_REQUESTS);
        }
    }

    @Test(description = "Without the automation field the exists rule still works and the automated rule cannot verify")
    void missingAutomationFieldOnlyStopsTheAutomatedRule() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            testRail.reply("/api/v2/get_case_fields", 200, "[{\"id\": 1, \"system_name\": \"custom_steps\"}]");
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageContaining("case does not exist in TestRail");
            assertThatThrownBy(() -> new TestRailCaseAutomatedRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("TestRail case field custom_automation_type not found")
                    .hasMessageContaining("TestRailCaseAutomatedRule");
        }
    }

    @Test(description = "Subclasses of both rules read the automation type from another field and share one load")
    void subclassesNameAnotherAutomationField() {
        try (FakeTestRail testRail = new FakeTestRail("custom_kind")) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());
            TestRailCaseExistsRule exists = new TestRailCaseExistsRule("custom_kind") {};
            TestRailCaseAutomatedRule automated =
                    new TestRailCaseAutomatedRule(Set.of("Automated"), Set.of("Manual"), "custom_kind") {};

            assertThatThrownBy(() -> exists.verify(context)).isInstanceOf(ArchitectureViolation.class);
            assertThatThrownBy(() -> automated.verify(context))
                    .isInstanceOf(ArchitectureViolation.class)
                    .hasMessageMatching("(?s).*C3 +OrderCasesTest\\.java:\\d+ \\(actual 'To automate', .*");
            assertThat(testRail.requests()).hasSize(LOAD_REQUESTS);
        }
    }

    @Test(description = "Rules naming different automation fields cannot share a load")
    void differentAutomationFieldsDoNotShareALoad() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());
            TestRailCaseAutomatedRule automated =
                    new TestRailCaseAutomatedRule(Set.of("Automated"), Set.of("Manual"), "custom_kind") {};

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class);
            assertThatThrownBy(() -> automated.verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("differing service configuration");
        }
    }

    @Test(description = "The automation field is part of the share key and must be a plain system name")
    void automationFieldIsPartOfTheShareKey() {
        assertThat(new TestRailCaseService().shareKey())
                .isEqualTo(new TestRailCaseService("custom_automation_type").shareKey())
                .isNotEqualTo(new TestRailCaseService("custom_kind").shareKey());
        assertThatThrownBy(() -> new TestRailCaseService(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestRailCaseService(" custom_kind")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestRailCaseService(null)).isInstanceOf(NullPointerException.class);
    }

    @Test(description = "The load reads every suite page, looks up each linked case and the foreign suite once")
    void loadRequestsMatchTheApi() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(ArchitectureViolation.class);

            assertThat(testRail.requests())
                    .containsExactlyInAnyOrder(
                            "/api/v2/get_case_fields",
                            "/api/v2/get_suites/7",
                            "/api/v2/get_suites/7&offset=1",
                            "/api/v2/get_case/1",
                            "/api/v2/get_case/2",
                            "/api/v2/get_case/3",
                            "/api/v2/get_case/4",
                            "/api/v2/get_case/5",
                            "/api/v2/get_case/9",
                            "/api/v2/get_case/10",
                            "/api/v2/get_case/1234567",
                            "/api/v2/get_suite/77");
        }
    }

    @Test(description = "Without linked cases the rules pass without variables and without requests")
    void noLinksNeedNothing() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(UNLINKED, Map.of());

            assertThatCode(() -> new TestRailCaseExistsRule().verify(context)).doesNotThrowAnyException();
            assertThatCode(() -> new TestRailCaseAutomatedRule().verify(context))
                    .doesNotThrowAnyException();
            assertThat(testRail.requests()).isEmpty();
        }
    }

    @Test(description = "Missing variables give the skip signal naming all four, before any request")
    void missingVariablesSkip() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            ArchUnitContext context = new FixtureContext(LINKED, Map.of(TestRailCaseService.USER, USER));

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, e -> {
                        assertThat(e.reason()).isEqualTo(Reason.MISSING_VARIABLES);
                        assertThat(e.missingVariables())
                                .containsExactly("TESTRAIL_URL", "TESTRAIL_PASSWORD", "TESTRAIL_PROJECT_ID");
                    });
            assertThat(testRail.requests()).isEmpty();
        }
    }

    @Test(description = "Rejected credentials fail as unable to verify, without user or password in the message")
    void rejectedCredentialsAreUnableToVerify() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            testRail.reply("/api/v2/get_case_fields", 401, "{\"error\": \"Authentication failed\"}");
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageStartingWith("TestRail rejected the credentials (HTTP 401)")
                    .hasMessageContaining("TESTRAIL_USER")
                    .satisfies(error -> assertNoSecrets(error));
        }
    }

    @Test(description = "A server error gives the service-unavailable signal for TestRail")
    void serverErrorIsUnavailable() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            testRail.reply("/api/v2/get_suites/7", 500, "{\"error\": \"internal\"}");
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, e -> {
                        assertThat(e.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE);
                        assertThat(e.service()).hasValue("TestRail");
                        assertThat(e).hasMessageContaining("returned 500");
                    })
                    .satisfies(error -> assertNoSecrets(error));
        }
    }

    @Test(description = "A malformed answer gives the service-unavailable signal")
    void malformedAnswerIsUnavailable() {
        try (FakeTestRail testRail = new FakeTestRail()) {
            testRail.reply("/api/v2/get_case/9", 200, "{\"id\": 9,");
            ArchUnitContext context = new FixtureContext(LINKED, testRail.variables());

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOfSatisfying(
                            ExternalDependencyUnavailable.class,
                            e -> assertThat(e.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE));
        }
    }

    @Test(description = "An unreachable server gives the service-unavailable signal")
    void unreachableServerIsUnavailable() {
        Map<String, String> variables;
        try (FakeTestRail testRail = new FakeTestRail()) {
            variables = testRail.variables();
        }
        ArchUnitContext context = new FixtureContext(LINKED, variables);

        assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                .isInstanceOfSatisfying(
                        ExternalDependencyUnavailable.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE));
    }

    @Test(description = "An unusable URL fails as unable to verify, naming the variable but not its value")
    void invalidUrlIsUnableToVerify() {
        Map<String, String> variables = new HashMap<>(Map.of(
                TestRailCaseService.URL, "ftp://" + USER + ":" + PASSWORD + "@tms.example.org",
                TestRailCaseService.USER, USER,
                TestRailCaseService.PASSWORD, PASSWORD,
                TestRailCaseService.PROJECT_ID, PROJECT));
        ArchUnitContext context = new FixtureContext(LINKED, variables);

        assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("TESTRAIL_URL is not a usable TestRail address: it must be an absolute http or https URL, "
                        + "for example https://tms.example.org")
                .satisfies(error -> assertNoSecrets(error))
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("ftp"));
    }

    @Test(description = "Each URL problem is named for TESTRAIL_URL without repeating the URL")
    void urlProblemsNameTheVariable() {
        Map<String, String> problems = Map.of(
                "tms.example.org",
                "must be an absolute http or https URL",
                "https://" + USER.replace("@", "%40") + ":" + PASSWORD + "@tms.example.org",
                "must not contain user info",
                "https://tms.example.org/?token=" + PASSWORD,
                "must not contain a query or fragment");
        problems.forEach((url, problem) -> {
            ArchUnitContext context = new FixtureContext(
                    LINKED,
                    Map.of(
                            TestRailCaseService.URL, url,
                            TestRailCaseService.USER, USER,
                            TestRailCaseService.PASSWORD, PASSWORD,
                            TestRailCaseService.PROJECT_ID, PROJECT));

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessage("TESTRAIL_URL is not a usable TestRail address: it %s, for example ".formatted(problem)
                            + "https://tms.example.org")
                    .satisfies(error -> assertNoSecrets(error));
        });
    }

    @Test(description = "A project id that is not a positive number fails as unable to verify")
    void invalidProjectIdIsUnableToVerify() {
        for (String projectId : List.of("abc", "0", "-3", "99999999999")) {
            Map<String, String> variables = Map.of(
                    TestRailCaseService.URL, "https://tms.example.org",
                    TestRailCaseService.USER, USER,
                    TestRailCaseService.PASSWORD, PASSWORD,
                    TestRailCaseService.PROJECT_ID, projectId);
            ArchUnitContext context = new FixtureContext(LINKED, variables);

            assertThatThrownBy(() -> new TestRailCaseExistsRule().verify(context))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessage("TESTRAIL_PROJECT_ID must be the positive numeric id of a TestRail project");
        }
    }

    @Test(description = "Rule metadata")
    void metadata() {
        TestRailCaseExistsRule exists = new TestRailCaseExistsRule();
        TestRailCaseAutomatedRule automated = new TestRailCaseAutomatedRule();

        assertThat(exists.name()).isEqualTo("TestRail case exists");
        assertThat(automated.name()).isEqualTo("TestRail case automated");
        for (var rule : List.of(exists, automated)) {
            assertThat(rule.severity()).isEqualTo(Severity.INFO);
            assertThat(rule.scopes()).isEqualTo(EnumSet.of(Scope.TEST));
            assertThat(rule.contextType()).isEqualTo(ArchUnitContext.class);
        }
        TestRailCaseService service = new TestRailCaseService();
        assertThat(service.displayName()).isEqualTo("TestRail");
        assertThat(service.requiredVariables())
                .containsExactly("TESTRAIL_URL", "TESTRAIL_USER", "TESTRAIL_PASSWORD", "TESTRAIL_PROJECT_ID");
    }

    private static void assertNoSecrets(Throwable error) {
        for (Throwable current = error; Objects.nonNull(current); current = current.getCause()) {
            assertThat(String.valueOf(current.getMessage()))
                    .doesNotContain(USER)
                    .doesNotContain(PASSWORD);
        }
    }

    /**
     * Serves project 7 with suites 11 and 12 (listed over two pages) and these cases: 1 Automated, 2 Manual, 3 To
     * automate, 4 without type, 5 of type 99, 10 deleted, 1234567 in suite 77 of project 3. Case 9 does not exist.
     */
    private static final class FakeTestRail implements AutoCloseable {

        private final HttpServer server;
        private final Map<String, Reply> replies = new ConcurrentHashMap<>();
        private final List<String> requests = new CopyOnWriteArrayList<>();
        private final ExecutorService executor = Executors.newCachedThreadPool();

        private final String automationField;

        FakeTestRail() {
            this("custom_automation_type");
        }

        FakeTestRail(String automationField) {
            this.automationField = automationField;
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
            reply("/api/v2/get_case_fields", 200, """
                    [
                      {"id": 1, "system_name": "custom_steps", "configs": []},
                      {"id": 2, "system_name": "%s", "label": "Automation Type",
                       "configs": [{"context": {"is_global": true},
                                    "options": {"items": "1, Automated\\n2, Manual\\n3, To automate"}}]}
                    ]
                    """.formatted(automationField));
            reply("/api/v2/get_suites/7", 200, """
                    {"offset": 0, "limit": 1, "size": 1,
                     "_links": {"next": "/api/v2/get_suites/7&offset=1", "prev": null},
                     "suites": [{"id": 11, "name": "Orders", "project_id": 7}]}
                    """);
            reply("/api/v2/get_suites/7&offset=1", 200, """
                    {"offset": 1, "limit": 1, "size": 1, "_links": {"next": null, "prev": "/api/v2/get_suites/7"},
                     "suites": [{"id": 12, "name": "Payments", "project_id": 7}]}
                    """);
            testCase(1, 11, 0, "1");
            testCase(2, 11, 0, "2");
            testCase(3, 11, 0, "3");
            testCase(4, 11, 0, "null");
            testCase(5, 12, 0, "99");
            testCase(10, 12, 1, "1");
            testCase(1234567, 77, 0, "1");
            reply("/api/v2/get_case/9", 400, "{\"error\": \"Field :case_id is not a valid test case.\"}");
            reply("/api/v2/get_suite/77", 200, "{\"id\": 77, \"name\": \"Catalog\", \"project_id\": 3}");
        }

        Map<String, String> variables() {
            return Map.of(
                    TestRailCaseService.URL,
                            "http://localhost:" + server.getAddress().getPort() + "/",
                    TestRailCaseService.USER, USER,
                    TestRailCaseService.PASSWORD, PASSWORD,
                    TestRailCaseService.PROJECT_ID, PROJECT);
        }

        void reply(String query, int status, String body) {
            replies.put(query, new Reply(status, body));
        }

        List<String> requests() {
            return requests;
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }

        private void testCase(int id, int suiteId, int deleted, String automationType) {
            reply("/api/v2/get_case/" + id, 200, """
                    {"id": %d, "suite_id": %d, "section_id": 1, "title": "Case %d", "is_deleted": %d,
                     "%s": %s}
                    """.formatted(
                            id, suiteId, id, deleted, automationField, automationType));
        }

        private void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(query);
            Reply reply = replies.getOrDefault(query, new Reply(404, "{}"));
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        private record Reply(int status, String body) {}
    }
}
