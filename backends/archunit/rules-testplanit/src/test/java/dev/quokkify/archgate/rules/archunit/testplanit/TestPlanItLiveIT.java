package dev.quokkify.archgate.rules.archunit.testplanit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Runs both rules against a real TestPlanIt that Docker Compose starts for this class, and removes it afterwards.
 *
 * <p>The rules read case ids from compiled test classes, so the fixture {@code archfixtures.testplanitlive} links
 * fixed ids. A fresh TestPlanIt seeds a demo project (id 1) with cases 1 to 5, so the cases this class creates get
 * the next ids in a known order: 6 automated, 7 manual, 8 soft-deleted, then a second project with case 9. Id
 * 999999 is never created. Seeding checks each created id and fails with a clear message if the server numbers them
 * differently, so the end-to-end path through {@code @TmsLink}, the shared load and the HTTP client stays real.
 *
 * <p>Runs only through {@code ./gradlew :rules-archunit-testplanit:testplanitIntegrationTest
 * -Parchgate.testplanitIt=true}; needs Docker with Compose v2.
 */
class TestPlanItLiveIT {

    private static final JavaClasses LIVE = new ClassFileImporter().importPackages("archfixtures.testplanitlive");
    private static final int DEMO_PROJECT = 1;
    private static final int AUTOMATED = 6;
    private static final int MANUAL = 7;
    private static final int DELETED = 8;
    private static final int FOREIGN = 9;
    private static final String ADMIN_EMAIL = "admin@example.org";
    private static final Duration START_LIMIT = Duration.ofMinutes(20);
    private static final Duration STOP_LIMIT = Duration.ofMinutes(3);
    private static final Duration REQUEST_LIMIT = Duration.ofSeconds(30);

    private final SecureRandom random = new SecureRandom();
    private final String project = "archgate-tpi-" + HexFormat.of().formatHex(random.generateSeed(4));
    private Path workDir;
    private Path composeFile;
    private Path log;
    private Map<String, String> composeEnvironment;
    private String baseUrl;
    private String token;
    private Thread stopOnExit;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(REQUEST_LIMIT)
            .build();
    private boolean started;

    @BeforeClass(alwaysRun = true)
    void start() throws Exception {
        String configured = System.getProperty("archgate.testplanit.workDir");
        workDir = Objects.nonNull(configured) ? Path.of(configured) : Files.createTempDirectory("testplanit-it");
        Files.createDirectories(workDir);
        composeFile = workDir.resolve("docker-compose.yml");
        try (InputStream in = Objects.requireNonNull(
                getClass().getResourceAsStream("/testplanit/docker-compose.yml"), "compose file resource")) {
            Files.copy(in, composeFile, StandardCopyOption.REPLACE_EXISTING);
        }
        log = workDir.resolve(project + ".log");
        int port = freePort();
        composeEnvironment = Map.of(
                "TESTPLANIT_PORT", String.valueOf(port),
                "TESTPLANIT_AUTH_SECRET", secret(32),
                "TESTPLANIT_ENCRYPTION_KEY", secret(32),
                "TESTPLANIT_ADMIN_EMAIL", ADMIN_EMAIL,
                "TESTPLANIT_ADMIN_PASSWORD", "Gate-" + secret(12) + "-1a");
        baseUrl = "http://127.0.0.1:" + port;
        stopOnExit = new Thread(this::stopQuietly, "testplanit-it-stop");
        Runtime.getRuntime().addShutdownHook(stopOnExit);
        started = true;
        compose(START_LIMIT, "up", "-d", "--wait", "--wait-timeout", "600");
        seed();
    }

    @AfterClass(alwaysRun = true)
    void stop() throws Exception {
        http.close();
        if (!started) {
            return;
        }
        compose(STOP_LIMIT, "down", "-v", "--remove-orphans");
        started = false;
        // Only now that the stack is gone; if down failed, the hook tries again when the JVM exits.
        try {
            Runtime.getRuntime().removeShutdownHook(stopOnExit);
        } catch (IllegalStateException shuttingDown) {
            // The JVM is exiting and runs the hook anyway; it finds nothing left to stop.
        }
    }

    @Test(description = "The exists rule reports the missing, the deleted and the other project's case")
    void existsRuleReportsBrokenLinks() {
        ArchUnitContext context = new FixtureContext(LIVE, variables(token, DEMO_PROJECT));

        assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageMatching(
                        "(?s).*case does not exist in TestPlanIt\":\\s+- C999999 +LiveCasesTest\\.java:\\d+.*")
                .hasMessageMatching("(?s).*case is deleted in TestPlanIt\":\\s+- C8 +LiveCasesTest\\.java:\\d+.*")
                .hasMessageMatching("(?s).*case belongs to another TestPlanIt project\":\\s+"
                        + "- C9 +LiveCasesTest\\.java:\\d+ \\(project 2\\).*")
                .satisfies(
                        violation -> assertThat(violation.getMessage()).doesNotContainPattern("C[67] +LiveCasesTest"));
    }

    @Test(description = "The automated rule reports the linked case that is not marked automated")
    void automatedRuleReportsTheManualCase() {
        ArchUnitContext context = new FixtureContext(LIVE, variables(token, DEMO_PROJECT));

        assertThatThrownBy(() -> new TestPlanItCaseAutomatedRule().verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageMatching("(?s).*C7 +LiveCasesTest\\.java:\\d+ \\(actual 'Manual', expected 'Automated'\\).*")
                .satisfies(violation ->
                        assertThat(violation.getMessage()).doesNotContainPattern("C(6|8|9|999999) +LiveCasesTest"));
    }

    @Test(description = "An unknown token is refused with 401 and fails the run as unable to verify")
    void unknownTokenIsUnableToVerify() {
        String unknown = "tpi_" + secret(16);
        ArchUnitContext context = new FixtureContext(LIVE, variables(unknown, DEMO_PROJECT));

        assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("TestPlanIt rejected the token (HTTP 401)")
                .satisfies(error -> assertNoSecret(error, unknown));
    }

    @Test(description = "A project the token cannot see fails the run as unable to verify")
    void unknownProjectIsUnableToVerify() {
        ArchUnitContext context = new FixtureContext(LIVE, variables(token, 4242));

        assertThatThrownBy(() -> new TestPlanItCaseExistsRule().verify(context))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("TestPlanIt project 4242 not found or the token cannot see it")
                .satisfies(error -> assertNoSecret(error, token));
    }

    @Test(
            description = "Deliberate canary: the server still answers an unauthenticated read with 200 and no "
                    + "rows, which the project check exists for. If TestPlanIt starts answering 401 here, update "
                    + "this test, not the client: the project check stays as a guard")
    void unauthenticatedReadLooksEmpty() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(modelUri(
                                "repositoryCases/findMany",
                                "{\"where\":{\"id\":{\"in\":[%d,%d]}},\"select\":{\"id\":true}}"
                                        .formatted(AUTOMATED, MANUAL)))
                        .timeout(REQUEST_LIMIT)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(Json.parse(response.body())).isEqualTo(Map.of("data", List.of()));
    }

    private Map<String, String> variables(String apiToken, int projectId) {
        return Map.of(
                TestPlanItCaseService.URL, baseUrl,
                TestPlanItCaseService.TOKEN, apiToken,
                TestPlanItCaseService.PROJECT_ID, String.valueOf(projectId));
    }

    /** Logs in as the seeded admin, mints an API token and creates the cases the fixture links. */
    private void seed() throws Exception {
        String csrf = (String) object(send(http, get("/api/auth/csrf"))).get("csrfToken");
        String form = form(Map.of(
                "csrfToken",
                csrf,
                "email",
                ADMIN_EMAIL,
                "password",
                composeEnvironment.get("TESTPLANIT_ADMIN_PASSWORD"),
                "json",
                "true"));
        HttpResponse<String> login = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/callback/credentials"))
                        .timeout(REQUEST_LIMIT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).as("admin login status").isBetween(200, 399);
        Object user = object(send(http, get("/api/auth/session"))).get("user");
        assertThat(user).as("admin session").isInstanceOf(Map.class);
        String userId = (String) ((Map<?, ?>) user).get("id");
        token = (String) object(send(
                        http,
                        HttpRequest.newBuilder(URI.create(baseUrl + "/api/api-tokens"))
                                .timeout(REQUEST_LIMIT)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"archgate-it\"}"))))
                .get("token");
        assertThat(token).as("minted API token").startsWith("tpi_");
        // From here on only the token authenticates, and the canary must go out without a session.
        cookies.getCookieStore().removeAll();

        long repository = id(read("repositories/findFirst", "{\"where\":{\"projectId\":%d}}".formatted(DEMO_PROJECT)));
        long folder = id(read(
                "repositoryFolders/findFirst",
                "{\"where\":{\"projectId\":%d,\"parentId\":null}}".formatted(DEMO_PROJECT)));
        long template = id(read("templates/findFirst", "{\"where\":{\"isDefault\":true}}"));
        long state = id(read("workflows/findFirst", "{\"where\":{\"scope\":\"CASES\",\"isDefault\":true}}"));
        Location demo = new Location(DEMO_PROJECT, repository, folder);

        expectId("automated case", createCase(demo, template, state, userId, "Automated case", true), AUTOMATED);
        expectId("manual case", createCase(demo, template, state, userId, "Manual case", false), MANUAL);
        expectId("deleted case", createCase(demo, template, state, userId, "Deleted case", false), DELETED);
        write(
                "PUT",
                "repositoryCases/update",
                "{\"where\":{\"id\":%d},\"data\":{\"isDeleted\":true}}".formatted(DELETED));

        long other = id(write(
                "POST", "projects/create", "{\"data\":{\"name\":\"Other\",\"createdBy\":\"%s\"}}".formatted(userId)));
        long otherRepository =
                id(write("POST", "repositories/create", "{\"data\":{\"projectId\":%d}}".formatted(other)));
        write(
                "POST",
                "templateProjectAssignment/create",
                "{\"data\":{\"projectId\":%d,\"templateId\":%d}}".formatted(other, template));
        write(
                "POST",
                "projectWorkflowAssignment/createMany",
                "{\"data\":[{\"projectId\":%d,\"workflowId\":%d}]}".formatted(other, state));
        long otherFolder = id(write(
                "POST",
                "repositoryFolders/create",
                "{\"data\":{\"projectId\":%d,\"repositoryId\":%d,\"name\":\"Root\",\"creatorId\":\"%s\"}}"
                        .formatted(other, otherRepository, userId)));
        expectId(
                "other project's case",
                createCase(new Location(other, otherRepository, otherFolder), template, state, userId, "Other", true),
                FOREIGN);
    }

    private long createCase(
            Location location, long template, long state, String userId, String name, boolean automated) {
        return id(write(
                "POST",
                "repositoryCases/create",
                ("{\"data\":{\"projectId\":%d,\"repositoryId\":%d,\"folderId\":%d,\"templateId\":%d,\"stateId\":%d,"
                                + "\"creatorId\":\"%s\",\"name\":\"%s\",\"automated\":%b}}")
                        .formatted(
                                location.project(),
                                location.repository(),
                                location.folder(),
                                template,
                                state,
                                userId,
                                name,
                                automated)));
    }

    private static void expectId(String what, long actual, int expected) {
        assertThat(actual)
                .as(
                        "TestPlanIt gave the %s id %d, but archfixtures.testplanitlive links %d; the seed of this "
                                + "TestPlanIt version numbers cases differently",
                        what, actual, expected)
                .isEqualTo(expected);
    }

    private Object read(String request, String arguments) {
        return data(send(
                http,
                withToken(HttpRequest.newBuilder(modelUri(request, arguments)).GET())));
    }

    private Object write(String method, String request, String body) {
        return data(send(
                http,
                withToken(HttpRequest.newBuilder(URI.create(baseUrl + "/api/model/" + request))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body)))));
    }

    private HttpRequest.Builder withToken(HttpRequest.Builder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
    }

    private URI modelUri(String request, String arguments) {
        return URI.create(
                baseUrl + "/api/model/" + request + "?q=" + URLEncoder.encode(arguments, StandardCharsets.UTF_8));
    }

    private static Object send(HttpClient client, HttpRequest.Builder request) {
        HttpRequest built = request.timeout(REQUEST_LIMIT).build();
        try {
            HttpResponse<String> response = client.send(built, HttpResponse.BodyHandlers.ofString());
            String path = built.uri().getPath();
            assertThat(response.statusCode())
                    .as("%s %s answered %s", built.method(), path, response.body())
                    .isBetween(200, 299);
            return Json.parse(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Map<?, ?> object(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private static Object data(Object body) {
        return object(body).get("data");
    }

    private static long id(Object data) {
        Object id = object(data).get("id");
        assertThat(id).isInstanceOf(Long.class);
        return (Long) id;
    }

    private static String form(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        fields.forEach((name, value) -> pairs.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return String.join("&", pairs);
    }

    private void compose(Duration limit, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                System.getProperty("archgate.testplanit.docker", "docker"),
                "compose",
                "-p",
                project,
                "-f",
                composeFile.toString()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Map<String, String> environment = new HashMap<>(composeEnvironment);
        builder.environment().putAll(environment);
        Process process = builder.start();
        if (!process.waitFor(limit.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "docker compose %s did not finish within %s; see %s".formatted(arguments[0], limit, log));
        }
        if (process.exitValue() != 0) {
            List<String> lines = Files.readAllLines(log);
            throw new IllegalStateException("docker compose %s failed with exit code %d; last lines of %s:%n%s"
                    .formatted(
                            arguments[0],
                            process.exitValue(),
                            log,
                            String.join(
                                    System.lineSeparator(),
                                    lines.subList(Math.max(0, lines.size() - 40), lines.size()))));
        }
    }

    private void stopQuietly() {
        if (!started) {
            return;
        }
        try {
            compose(STOP_LIMIT, "down", "-v", "--remove-orphans");
        } catch (IOException | RuntimeException e) {
            // Best effort while the JVM exits; the log names the project to remove by hand.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String secret(int bytes) {
        byte[] value = new byte[bytes];
        random.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    private static void assertNoSecret(Throwable error, String secret) {
        for (Throwable current = error; Objects.nonNull(current); current = current.getCause()) {
            assertThat(String.valueOf(current.getMessage())).doesNotContain(secret);
        }
    }

    private record Location(long project, long repository, long folder) {}
}
