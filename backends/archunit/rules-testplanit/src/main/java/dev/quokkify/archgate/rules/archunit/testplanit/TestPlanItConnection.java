package dev.quokkify.archgate.rules.archunit.testplanit;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Where and how to reach TestPlanIt, and how much work one load may spend.
 *
 * @param baseUrl        TestPlanIt server URL, for example {@code https://tms.example.org}; requests go to
 *                       {@code <baseUrl>/api/model/...}
 * @param token          API token sent as a Bearer token; never printed
 * @param projectId      project the rules check against
 * @param connectTimeout limit for opening a connection
 * @param readTimeout    limit for one whole exchange, further capped by what is left of {@code budget}
 * @param budget         limit for a whole load
 * @param chunkSize      number of case ids asked for in one request
 */
record TestPlanItConnection(
        String baseUrl,
        String token,
        int projectId,
        Duration connectTimeout,
        Duration readTimeout,
        Duration budget,
        int chunkSize) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration DEFAULT_BUDGET = Duration.ofSeconds(60);
    private static final int DEFAULT_CHUNK_SIZE = 200;
    private static final String NOT_HTTP = "must be an absolute http or https URL";

    TestPlanItConnection {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
        Objects.requireNonNull(budget, "budget");
        urlProblem(baseUrl).ifPresent(problem -> {
            throw new IllegalArgumentException("baseUrl " + problem);
        });
        if (projectId <= 0) {
            throw new IllegalArgumentException("projectId must be positive: " + projectId);
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive: " + chunkSize);
        }
        requireWholeMillis("connectTimeout", connectTimeout);
        requireWholeMillis("readTimeout", readTimeout);
        requireWholeMillis("budget", budget);
    }

    /** Creates a connection with a 10 s connect timeout, a 30 s read timeout, a 60 s budget and chunks of 200. */
    static TestPlanItConnection of(String baseUrl, String token, int projectId) {
        return new TestPlanItConnection(
                baseUrl,
                token,
                projectId,
                DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_READ_TIMEOUT,
                DEFAULT_BUDGET,
                DEFAULT_CHUNK_SIZE);
    }

    @Override
    public String toString() {
        return ("TestPlanItConnection[baseUrl=%s, token=***, projectId=%d, connectTimeout=%s, readTimeout=%s, "
                        + "budget=%s, chunkSize=%d]")
                .formatted(baseUrl, projectId, connectTimeout, readTimeout, budget, chunkSize);
    }

    /**
     * Tells what makes a server URL unusable, without repeating the URL, which may hold credentials.
     *
     * @param baseUrl the URL to check
     * @return the problem, such as {@code must not contain user info}, or empty when the URL is usable
     */
    static Optional<String> urlProblem(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            return Optional.of(NOT_HTTP);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || Objects.isNull(uri.getHost())
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return Optional.of(NOT_HTTP);
        }
        if (Objects.nonNull(uri.getRawUserInfo())) {
            return Optional.of("must not contain user info");
        }
        if (Objects.nonNull(uri.getRawQuery()) || Objects.nonNull(uri.getRawFragment())) {
            return Optional.of("must not contain a query or fragment");
        }
        return Optional.empty();
    }

    private static void requireWholeMillis(String name, Duration value) {
        if (value.toMillis() < 1) {
            throw new IllegalArgumentException(name + " must be at least 1 ms: " + value);
        }
    }
}
