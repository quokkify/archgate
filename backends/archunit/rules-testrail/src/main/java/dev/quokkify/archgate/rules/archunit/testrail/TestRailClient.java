package dev.quokkify.archgate.rules.archunit.testrail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads TestRail through its REST API v2 with the JDK HTTP client. Every request is a {@code GET} of
 * {@code <baseUrl>/index.php?/api/v2/<endpoint>} with preemptive Basic authentication. A non-2xx status raises
 * {@link TestRailApiException}, except 400 on {@code get_case}, which TestRail returns for an unknown case. Transport
 * failures and timeouts raise {@link UncheckedIOException}; unexpected bodies raise {@link IllegalStateException}. No
 * message carries the user name or password. The request timeout bounds each whole exchange, body included. Close the
 * client when the run is done to release its HTTP client threads.
 */
final class TestRailClient implements CaseSource, AutoCloseable {

    static final int MAX_SUITE_PAGES = 1000;

    private static final String API_PREFIX = "api/v2/";
    private static final String MEDIA_TYPE = "application/json";
    private static final int BAD_REQUEST = 400;

    private final TestRailConnection connection;
    private final String apiRoot;
    private final String authorization;
    private final HttpClient http;
    private final String automationField;

    TestRailClient(TestRailConnection connection) {
        this(connection, CaseRecord.AUTOMATION_TYPE_FIELD);
    }

    TestRailClient(TestRailConnection connection, String automationField) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.automationField = Objects.requireNonNull(automationField, "automationField");
        String base = connection.baseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.apiRoot = base + "/index.php?/" + API_PREFIX;
        String credentials = connection.user() + ":" + connection.password();
        this.authorization =
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connection.connectTimeout())
                .build();
    }

    @Override
    public int projectId() {
        return connection.projectId();
    }

    @Override
    public Optional<CaseRecord> findCase(int id) {
        String endpoint = "get_case/" + id;
        HttpResponse<String> response = send(endpoint);
        if (response.statusCode() == BAD_REQUEST) {
            return Optional.empty();
        }
        requireSuccess(endpoint, response);
        return Optional.of(
                CaseRecord.from(endpoint, JsonValues.object(endpoint, body(endpoint, response)), automationField));
    }

    @Override
    public List<CaseRecord> suitePage(int suiteId, int offset) {
        String endpoint = "get_cases/%d&suite_id=%d&limit=%d&offset=%d"
                .formatted(connection.projectId(), suiteId, PAGE_SIZE, offset);
        List<CaseRecord> cases = new ArrayList<>();
        for (Object element : collection(endpoint, get(endpoint), "cases")) {
            Map<?, ?> json = JsonValues.object(endpoint, element);
            if (JsonValues.integer(json.get("id")).isPresent()) {
                cases.add(CaseRecord.from(endpoint, json, automationField));
            }
        }
        return List.copyOf(cases);
    }

    @Override
    public List<FieldRecord> caseFields() {
        String endpoint = "get_case_fields";
        return FieldRecord.listFrom(endpoint, get(endpoint));
    }

    @Override
    public Set<Integer> projectSuiteIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        String first = "get_suites/" + connection.projectId();
        String endpoint = first;
        while (Objects.nonNull(endpoint)) {
            if (visited.size() == MAX_SUITE_PAGES) {
                throw new IllegalStateException(
                        "TestRail %s returned more than %d pages".formatted(first, MAX_SUITE_PAGES));
            }
            if (!visited.add(endpoint)) {
                throw new IllegalStateException("TestRail %s next link repeats a page".formatted(endpoint));
            }
            Object page = get(endpoint);
            for (Object element : collection(endpoint, page, "suites")) {
                JsonValues.integer(JsonValues.object(endpoint, element).get("id"))
                        .ifPresent(ids::add);
            }
            endpoint = next(endpoint, page);
        }
        return Set.copyOf(ids);
    }

    @Override
    public int suiteProjectId(int suiteId) {
        return suite(suiteId).projectId();
    }

    SuiteRecord suite(int suiteId) {
        String endpoint = "get_suite/" + suiteId;
        return SuiteRecord.from(endpoint, JsonValues.object(endpoint, get(endpoint)));
    }

    private Object get(String endpoint) {
        HttpResponse<String> response = send(endpoint);
        requireSuccess(endpoint, response);
        return body(endpoint, response);
    }

    private HttpResponse<String> send(String endpoint) {
        HttpRequest request = HttpRequest.newBuilder(uri(endpoint))
                .GET()
                .timeout(connection.requestTimeout())
                .header("Authorization", authorization)
                .header("Accept", MEDIA_TYPE)
                .header("Content-Type", MEDIA_TYPE)
                .build();
        CompletableFuture<HttpResponse<String>> exchange =
                http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return exchange.get(connection.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw timedOut(endpoint, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException timeout) {
                throw timedOut(endpoint, timeout);
            }
            if (cause instanceof IOException io) {
                throw new UncheckedIOException(
                        "TestRail %s request failed: %s"
                                .formatted(endpoint, io.getClass().getSimpleName()),
                        io);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("TestRail %s request failed".formatted(endpoint), cause);
        } catch (InterruptedException e) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TestRail %s request interrupted".formatted(endpoint), e);
        }
    }

    /** Stops the HTTP client; requests made afterwards fail with {@link UncheckedIOException}. */
    @Override
    public void close() {
        http.shutdownNow();
    }

    private UncheckedIOException timedOut(String endpoint, Exception cause) {
        IOException io = cause instanceof IOException direct ? direct : new HttpTimeoutException(cause.toString());
        return new UncheckedIOException(
                "TestRail %s timed out after %d ms"
                        .formatted(endpoint, connection.requestTimeout().toMillis()),
                io);
    }

    private URI uri(String endpoint) {
        try {
            return URI.create(apiRoot + endpoint);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TestRail endpoint %s is not a valid URL".formatted(endpoint), e);
        }
    }

    private static void requireSuccess(String endpoint, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new TestRailApiException(endpoint, status);
        }
    }

    private static Object body(String endpoint, HttpResponse<String> response) {
        try {
            return Json.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "TestRail %s returned malformed JSON: %s".formatted(endpoint, e.getMessage()), e);
        }
    }

    private static List<?> collection(String endpoint, Object page, String name) {
        if (page instanceof List<?> plain) {
            return plain;
        }
        if (page instanceof Map<?, ?> object && object.get(name) instanceof List<?> values) {
            return values;
        }
        throw new IllegalStateException("TestRail %s response has no %s collection".formatted(endpoint, name));
    }

    private static String next(String endpoint, Object page) {
        if (page instanceof Map<?, ?> object
                && object.get("_links") instanceof Map<?, ?> links
                && links.get("next") instanceof String next
                && !next.isBlank()) {
            String relative = next.startsWith("/") ? next.substring(1) : next;
            if (!relative.startsWith(API_PREFIX)) {
                throw new IllegalStateException("TestRail %s returned a next link outside the API".formatted(endpoint));
            }
            return relative.substring(API_PREFIX.length());
        }
        return null;
    }
}
