package dev.quokkify.archgate.rules.archunit.testplanit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads TestPlanIt through its model API with the JDK HTTP client. Every request is a {@code GET} of
 * {@code <baseUrl>/api/model/<model>/<operation>?q=<arguments as JSON>} with the API token as a Bearer token, and the
 * answer is the {@code data} member of the JSON body. A non-2xx status raises {@link TestPlanItApiException};
 * transport failures and timeouts raise {@link UncheckedIOException}; unexpected bodies raise
 * {@link IllegalStateException}. No message carries the token. The timeout given with a request bounds the whole
 * exchange, body included, and redirects are not followed. Close the client when the load is done.
 */
final class TestPlanItClient implements AutoCloseable {

    private static final String MEDIA_TYPE = "application/json";
    private static final String DATA = "data";

    private final String apiRoot;
    private final String authorization;
    private final HttpClient http;

    TestPlanItClient(TestPlanItConnection connection) {
        Objects.requireNonNull(connection, "connection");
        String base = connection.baseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.apiRoot = base + "/api/model/";
        this.authorization = "Bearer " + connection.token();
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connection.connectTimeout())
                .build();
    }

    /**
     * Runs one read operation.
     *
     * @param request the model and operation, for example {@code repositoryCases/findMany}
     * @param arguments the operation's arguments as a JSON object
     * @param timeout the limit for the whole exchange
     * @return the {@code data} member of the answer, which may be {@code null}
     */
    Object read(String request, String arguments, Duration timeout) {
        HttpResponse<String> response = send(request, arguments, timeout);
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new TestPlanItApiException(request, status);
        }
        Object body;
        try {
            body = Json.parse(response.body());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "TestPlanIt %s returned malformed JSON: %s".formatted(request, e.getMessage()), e);
        }
        if (body instanceof Map<?, ?> object && object.containsKey(DATA)) {
            return object.get(DATA);
        }
        throw new IllegalStateException("TestPlanIt %s response has no data member".formatted(request));
    }

    /** Stops the HTTP client; requests made afterwards fail with {@link UncheckedIOException}. */
    @Override
    public void close() {
        http.shutdownNow();
    }

    private HttpResponse<String> send(String request, String arguments, Duration timeout) {
        HttpRequest httpRequest = HttpRequest.newBuilder(uri(request, arguments))
                .GET()
                .timeout(timeout)
                .header("Authorization", authorization)
                .header("Accept", MEDIA_TYPE)
                .build();
        CompletableFuture<HttpResponse<String>> exchange =
                http.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw timedOut(request, timeout, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException httpTimeout) {
                throw timedOut(request, timeout, httpTimeout);
            }
            if (cause instanceof IOException io) {
                throw new UncheckedIOException(
                        "TestPlanIt %s request failed: %s"
                                .formatted(request, io.getClass().getSimpleName()),
                        io);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("TestPlanIt %s request failed".formatted(request), cause);
        } catch (InterruptedException e) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("TestPlanIt %s request interrupted".formatted(request), e);
        }
    }

    private static UncheckedIOException timedOut(String request, Duration timeout, Exception cause) {
        IOException io = cause instanceof IOException direct ? direct : new HttpTimeoutException(cause.toString());
        return new UncheckedIOException(
                "TestPlanIt %s timed out after %d ms".formatted(request, timeout.toMillis()), io);
    }

    private URI uri(String request, String arguments) {
        try {
            return URI.create(apiRoot + request + "?q=" + URLEncoder.encode(arguments, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("TestPlanIt request %s is not a valid URL".formatted(request), e);
        }
    }
}
