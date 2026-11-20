package dev.quokkify.archgate.rules.archunit.testrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

class TestRailClientTest {

    private static final String USER = "reader@example.org";
    private static final String PASSWORD = "s3cret-key";
    private static final int PROJECT = 7;

    @Test
    void sendsPreemptiveBasicAuthAndJsonHeaders() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case_fields", 200, "[]");

            stub.client().caseFields();

            assertThat(stub.requests()).hasSize(1);
            Request request = stub.requests().get(0);
            String expected = "Basic "
                    + Base64.getEncoder().encodeToString((USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
            assertThat(request.authorization()).isEqualTo(expected);
            assertThat(request.accept()).isEqualTo("application/json");
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.path()).isEqualTo("/index.php");
        }
    }

    @Test
    void readsCaseFields() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case_fields", 200, """
                    [
                      {"id": 1, "system_name": "custom_tags", "configs": [{"options": {"items": "1, Red"}}]},
                      {"id": 2, "system_name": "custom_automation_type",
                       "configs": [{"options": {"items": "0, None\\n2, Automated\\n5, Manual"}}]},
                      {"id": 3, "system_name": "custom_notes", "configs": []}
                    ]
                    """);

            List<FieldRecord> fields = stub.client().caseFields();

            assertThat(stub.requests()).extracting(Request::query).containsExactly("/api/v2/get_case_fields");
            assertThat(fields)
                    .containsExactly(
                            new FieldRecord("custom_tags", "1, Red"),
                            new FieldRecord("custom_automation_type", "0, None\n2, Automated\n5, Manual"),
                            new FieldRecord("custom_notes", null));
            assertThat(AutomationTypes.parse(fields))
                    .containsExactly(Map.entry(0, "None"), Map.entry(2, "Automated"), Map.entry(5, "Manual"));
        }
    }

    @Test
    void findsCase() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case/123", 200, """
                    {"id": 123, "suite_id": 4, "is_deleted": 0, "custom_automation_type": 2, "title": "Order is paid"}
                    """);

            Optional<CaseRecord> found = stub.client().findCase(123);

            assertThat(stub.requests()).extracting(Request::query).containsExactly("/api/v2/get_case/123");
            assertThat(found).contains(new CaseRecord(123, 4, false, 2));
        }
    }

    @Test
    void readsDeletedCaseAndAbsentOptionalFields() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case/9", 200, "{\"id\": 9, \"suite_id\": null, \"is_deleted\": 1}");

            assertThat(stub.client().findCase(9)).contains(new CaseRecord(9, null, true, null));
        }
    }

    @Test
    void treatsBadRequestOnGetCaseAsMissingCase() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case/404", 400, "{\"error\": \"Field :case_id is not a valid test case.\"}");

            assertThat(stub.client().findCase(404)).isEmpty();
        }
    }

    @Test
    void readsOneSuitePageWithLimitAndOffsetWithoutFollowingNext() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_cases/7&suite_id=4&limit=250&offset=500", 200, """
                    {"offset": 500, "limit": 250, "size": 2,
                     "_links": {"next": "/api/v2/get_cases/7&suite_id=4&limit=250&offset=750", "prev": null},
                     "cases": [{"id": 1, "suite_id": 4, "custom_automation_type": 5}, {"title": "no id"},
                               {"id": 2, "suite_id": 4, "is_deleted": 1}]}
                    """);

            List<CaseRecord> page = stub.client().suitePage(4, 500);

            assertThat(stub.requests())
                    .extracting(Request::query)
                    .containsExactly("/api/v2/get_cases/7&suite_id=4&limit=250&offset=500");
            assertThat(page).containsExactly(new CaseRecord(1, 4, false, 5), new CaseRecord(2, 4, true, null));
        }
    }

    @Test
    void acceptsAPlainArrayAsASuitePage() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_cases/7&suite_id=4&limit=250&offset=0", 200, "[{\"id\": 1, \"suite_id\": 4}]");

            assertThat(stub.client().suitePage(4, 0)).containsExactly(new CaseRecord(1, 4, false, null));
        }
    }

    @Test
    void rejectsSuitePageWithoutCases() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_cases/7&suite_id=4&limit=250&offset=0", 200, "{\"size\": 0}");

            assertThatThrownBy(() -> stub.client().suitePage(4, 0))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TestRail get_cases/7&suite_id=4&limit=250&offset=0 response has no cases collection");
        }
    }

    @Test
    void followsNextLinksForProjectSuites() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suites/7", 200, """
                    {"suites": [{"id": 1, "project_id": 7}, {"id": 2, "project_id": 7}],
                     "_links": {"next": "/api/v2/get_suites/7&limit=2&offset=2"}}
                    """);
            stub.reply("get_suites/7&limit=2&offset=2", 200, """
                    {"suites": [{"id": 3, "project_id": 7}, {"name": "no id"}], "_links": {"next": null}}
                    """);

            assertThat(stub.client().projectSuiteIds()).containsExactlyInAnyOrder(1, 2, 3);
            assertThat(stub.requests())
                    .extracting(Request::query)
                    .containsExactly("/api/v2/get_suites/7", "/api/v2/get_suites/7&limit=2&offset=2");
        }
    }

    @Test
    void acceptsAPlainArrayOfSuites() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suites/7", 200, "[{\"id\": 5, \"project_id\": 7}]");

            assertThat(stub.client().projectSuiteIds()).containsExactly(5);
        }
    }

    @Test
    void rejectsSuitesResponseWithoutSuites() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suites/7", 200, "{\"_links\": {}}");

            assertThatThrownBy(() -> stub.client().projectSuiteIds())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TestRail get_suites/7 response has no suites collection");
        }
    }

    @Test
    void stopsWhenANextLinkRepeats() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suites/7", 200, "{\"suites\": [], \"_links\": {\"next\": \"/api/v2/get_suites/7\"}}");

            assertThatThrownBy(() -> stub.client().projectSuiteIds())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("get_suites/7")
                    .hasMessageContaining("repeats");
        }
    }

    @Test
    void readsTheProjectOfASuite() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suite/12", 200, "{\"id\": 12, \"project_id\": 3, \"name\": \"Catalog\"}");

            assertThat(stub.client().suiteProjectId(12)).isEqualTo(3);
            assertThat(stub.client().suite(12)).isEqualTo(new SuiteRecord(12, 3));
            assertThat(stub.requests()).extracting(Request::query).containsOnly("/api/v2/get_suite/12");
        }
    }

    @Test
    void rejectsSuiteWithoutProject() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suite/12", 200, "{\"id\": 12}");

            assertThatThrownBy(() -> stub.client().suiteProjectId(12))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TestRail get_suite/12 response has no integer project_id");
        }
    }

    @Test
    void reportsTheProjectOfTheConnection() {
        try (StubTestRail stub = new StubTestRail()) {
            assertThat(stub.client().projectId()).isEqualTo(PROJECT);
        }
    }

    @Test
    void failsOnServerErrorWithEndpointAndStatusButNoCredentials() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case/5", 500, "{\"error\": \"" + USER + "\"}");

            assertThatThrownBy(() -> stub.client().findCase(5))
                    .isInstanceOfSatisfying(TestRailApiException.class, e -> {
                        assertThat(e.statusCode()).isEqualTo(500);
                        assertThat(e.endpoint()).isEqualTo("get_case/5");
                    })
                    .hasMessage("TestRail get_case/5 returned 500")
                    .hasMessageNotContaining(USER)
                    .hasMessageNotContaining(PASSWORD);
        }
    }

    @Test
    void failsOnBadRequestOutsideGetCase() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_suite/99", 400, "{}");

            assertThatThrownBy(() -> stub.client().suiteProjectId(99))
                    .isInstanceOfSatisfying(
                            TestRailApiException.class,
                            e -> assertThat(e.statusCode()).isEqualTo(400))
                    .hasMessage("TestRail get_suite/99 returned 400");
        }
    }

    @Test
    void failsOnRejectedCredentialsAndRateLimit() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case_fields", 401, "{}");
            stub.reply("get_suites/7", 429, "{}");

            assertThatThrownBy(() -> stub.client().caseFields())
                    .isInstanceOfSatisfying(
                            TestRailApiException.class,
                            e -> assertThat(e.statusCode()).isEqualTo(401));
            assertThatThrownBy(() -> stub.client().projectSuiteIds())
                    .isInstanceOfSatisfying(
                            TestRailApiException.class,
                            e -> assertThat(e.statusCode()).isEqualTo(429));
        }
    }

    @Test
    void failsClearlyOnMalformedBody() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case/5", 200, "<html>login</html>");

            assertThatThrownBy(() -> stub.client().findCase(5))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("TestRail get_case/5 returned malformed JSON: Malformed JSON")
                    .hasMessageNotContaining("login")
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void failsClearlyOnUnexpectedShape() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case/5", 200, "[1, 2]");

            assertThatThrownBy(() -> stub.client().findCase(5))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TestRail get_case/5 response is not a JSON object");
        }
    }

    @Test
    void failsWhenTheServerDoesNotAnswerWithinTheReadTimeout() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.hang("get_case/5");
            TestRailConnection connection = new TestRailConnection(
                    stub.baseUrl(),
                    USER,
                    PASSWORD,
                    PROJECT,
                    Duration.ofSeconds(2),
                    Duration.ofMillis(200),
                    1,
                    Duration.ofSeconds(5),
                    0);

            long started = System.nanoTime();
            assertThatThrownBy(() -> new TestRailClient(connection).findCase(5))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessage("TestRail get_case/5 timed out after 200 ms")
                    .hasMessageNotContaining(PASSWORD);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test
    void capsTheReadTimeoutByTheBudget() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.hang("get_case/5");
            TestRailConnection connection = new TestRailConnection(
                    stub.baseUrl(),
                    USER,
                    PASSWORD,
                    PROJECT,
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(30),
                    1,
                    Duration.ofMillis(150),
                    0);

            assertThatThrownBy(() -> new TestRailClient(connection).findCase(5))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessage("TestRail get_case/5 timed out after 150 ms");
        }
    }

    @Test
    void failsWithoutCredentialsWhenTheServerIsUnreachable() throws IOException {
        try (StubTestRail stub = new StubTestRail()) {
            int port;
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                port = socket.getLocalPort();
            }
            TestRailClient unreachable =
                    new TestRailClient(TestRailConnection.of("http://127.0.0.1:" + port, USER, PASSWORD, PROJECT));

            assertThatThrownBy(unreachable::caseFields)
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessageStartingWith("TestRail get_case_fields request failed: ")
                    .hasMessageNotContaining(USER)
                    .hasMessageNotContaining(PASSWORD);
        }
    }

    @Test
    void acceptsABaseUrlWithATrailingSlashOrPath() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case_fields", 200, "[]");

            new TestRailClient(TestRailConnection.of(stub.baseUrl() + "/", USER, PASSWORD, PROJECT)).caseFields();

            assertThat(stub.requests().get(0).path()).isEqualTo("/index.php");
        }
    }

    @Test
    void failsWhenTheBodyStallsAfterTheHeaders() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.stallBody("get_case/5");
            TestRailConnection connection = new TestRailConnection(
                    stub.baseUrl(),
                    USER,
                    PASSWORD,
                    PROJECT,
                    Duration.ofSeconds(2),
                    Duration.ofMillis(300),
                    1,
                    Duration.ofSeconds(5),
                    0);

            long started = System.nanoTime();
            try (TestRailClient client = new TestRailClient(connection)) {
                assertThatThrownBy(() -> client.findCase(5))
                        .isInstanceOf(UncheckedIOException.class)
                        .hasMessage("TestRail get_case/5 timed out after 300 ms");
            }
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test
    void stopsPagingSuitesAfterThePageCap() {
        try (StubTestRail stub = new StubTestRail();
                TestRailClient client = stub.client()) {
            stub.pageSuitesForever();

            assertThatThrownBy(client::projectSuiteIds)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TestRail get_suites/7 returned more than 1000 pages");
            assertThat(stub.requests()).hasSize(1000);
        }
    }

    @Test
    void rejectsANextLinkOutsideTheApi() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply(
                    "get_suites/7",
                    200,
                    "{\"suites\": [], \"_links\": {\"next\": \"https://elsewhere.example.org/index.php?/api/v2/get_suites/7\"}}");

            assertThatThrownBy(() -> stub.client().projectSuiteIds())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TestRail get_suites/7 returned a next link outside the API");
            assertThat(stub.requests()).hasSize(1);
        }
    }

    @Test
    void refusesRequestsAfterClose() {
        try (StubTestRail stub = new StubTestRail()) {
            stub.reply("get_case_fields", 200, "[]");
            TestRailClient client = stub.client();
            client.close();
            client.close();

            assertThatThrownBy(client::caseFields).isInstanceOf(UncheckedIOException.class);
        }
    }

    private static final class StubTestRail implements AutoCloseable {

        private final HttpServer server;
        private final Map<String, Reply> replies = new ConcurrentHashMap<>();
        private final List<Request> requests = new CopyOnWriteArrayList<>();
        private final CountDownLatch release = new CountDownLatch(1);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private volatile boolean pageForever;

        StubTestRail() {
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.setExecutor(executor);
            server.createContext("/", this::handle);
            server.start();
        }

        TestRailClient client() {
            return new TestRailClient(TestRailConnection.of(baseUrl(), USER, PASSWORD, PROJECT));
        }

        String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        void reply(String endpoint, int status, String body) {
            replies.put("/api/v2/" + endpoint, new Reply(status, body, Mode.NORMAL));
        }

        void hang(String endpoint) {
            replies.put("/api/v2/" + endpoint, new Reply(200, "{}", Mode.HANG));
        }

        void stallBody(String endpoint) {
            replies.put("/api/v2/" + endpoint, new Reply(200, "{\"id\": 1}", Mode.STALL_BODY));
        }

        void pageSuitesForever() {
            pageForever = true;
        }

        List<Request> requests() {
            return requests;
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            requests.add(new Request(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath(),
                    query,
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Accept")));
            Reply reply = replies.getOrDefault(query, new Reply(404, "{}", Mode.NORMAL));
            if (pageForever && query.startsWith("/api/v2/get_suites/")) {
                reply = new Reply(200, "{\"suites\": [], \"_links\": {\"next\": \"" + query + "&p\"}}", Mode.NORMAL);
            }
            if (reply.mode() == Mode.HANG) {
                awaitRelease();
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (reply.mode() == Mode.STALL_BODY) {
                exchange.sendResponseHeaders(reply.status(), 100);
                OutputStream out = exchange.getResponseBody();
                out.write(body, 0, 4);
                out.flush();
                awaitRelease();
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(reply.status(), body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }

        private void awaitRelease() {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private enum Mode {
        NORMAL,
        HANG,
        STALL_BODY
    }

    private record Reply(int status, String body, Mode mode) {}

    private record Request(String method, String path, String query, String authorization, String accept) {}
}
