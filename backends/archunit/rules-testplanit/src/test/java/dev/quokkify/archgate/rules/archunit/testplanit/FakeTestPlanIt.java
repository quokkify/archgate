package dev.quokkify.archgate.rules.archunit.testplanit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Loopback server that answers like the TestPlanIt model API, with the response shapes a live instance returned.
 * It serves projects 3 and 7 and these cases: 1 automated and 2 manual in project 7, 10 deleted in project 7,
 * 1234567 in project 3. Like the real server, a request without a known token sees nothing: {@code findUnique}
 * answers {@code {"data":null}} and {@code findMany} answers {@code {"data":[]}}, both with status 200; a request
 * with an unknown {@code tpi_} token gets 401.
 */
final class FakeTestPlanIt implements AutoCloseable {

    static final String TOKEN = "tpi_fakeTokenValue0123456789abcdef";
    static final String PROJECT = "7";

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Reply> overrides = new ConcurrentHashMap<>();
    private final Map<Integer, Row> cases = new ConcurrentHashMap<>(Map.of(
            1, new Row(1, 7, false, true),
            2, new Row(2, 7, false, false),
            10, new Row(10, 7, true, false),
            1234567, new Row(1234567, 3, false, true)));
    private volatile boolean anonymous;
    private volatile Duration delay = Duration.ZERO;

    FakeTestPlanIt() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    Map<String, String> variables() {
        return Map.of(
                TestPlanItCaseService.URL, url(),
                TestPlanItCaseService.TOKEN, TOKEN,
                TestPlanItCaseService.PROJECT_ID, PROJECT);
    }

    /** Treats every request as unauthenticated, as the real server does when it ignores the token. */
    void anonymous() {
        anonymous = true;
    }

    /** Waits this long before every answer. */
    void delay(Duration value) {
        delay = value;
    }

    /** Answers every request for {@code path}, such as {@code repositoryCases/findMany}, with this reply. */
    void reply(String path, int status, String body) {
        overrides.put(path, new Reply(status, body));
    }

    /** Each request as {@code <model>/<operation> <decoded q>}. */
    List<String> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().replaceFirst("^/api/model/", "");
        String query = Objects.requireNonNullElse(exchange.getRequestURI().getRawQuery(), "");
        String arguments = query.startsWith("q=") ? URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8) : "";
        requests.add(path + " " + arguments);
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Reply reply = overrides.containsKey(path) ? overrides.get(path) : answer(exchange, path, arguments);
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private Reply answer(HttpExchange exchange, String path, String arguments) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        boolean authenticated = !anonymous && ("Bearer " + TOKEN).equals(authorization);
        if (!anonymous && Objects.nonNull(authorization) && !authenticated) {
            return new Reply(401, "{\"error\":\"Invalid API token\",\"code\":\"INVALID_TOKEN\"}");
        }
        Map<?, ?> args = (Map<?, ?>) Json.parse(arguments);
        Map<?, ?> where = (Map<?, ?>) args.get("where");
        return switch (path) {
            case "projects/findUnique" -> {
                long id = (Long) where.get("id");
                yield new Reply(
                        200,
                        authenticated && (id == 3 || id == 7) ? "{\"data\":{\"id\":%d}}".formatted(id) : DATA_NULL);
            }
            case "repositoryCases/findMany" -> {
                List<?> ids = (List<?>) ((Map<?, ?>) where.get("id")).get("in");
                String rows = authenticated
                        ? ids.stream()
                                .map(id -> cases.get(((Long) id).intValue()))
                                .filter(Objects::nonNull)
                                .map(Row::json)
                                .collect(Collectors.joining(","))
                        : "";
                yield new Reply(200, "{\"data\":[" + rows + "]}");
            }
            default -> new Reply(400, "{\"error\":{\"message\":\"unknown model name\"}}");
        };
    }

    private static final String DATA_NULL = "{\"data\":null}";

    private record Row(int id, int projectId, boolean isDeleted, boolean automated) {

        String json() {
            return "{\"id\":%d,\"projectId\":%d,\"automated\":%b,\"isDeleted\":%b}"
                    .formatted(id, projectId, automated, isDeleted);
        }
    }

    private record Reply(int status, String body) {}
}
