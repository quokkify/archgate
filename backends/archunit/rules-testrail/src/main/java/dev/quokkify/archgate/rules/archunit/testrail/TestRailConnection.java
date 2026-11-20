package dev.quokkify.archgate.rules.archunit.testrail;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Where and how to reach TestRail, and how much work one index load may spend.
 *
 * @param baseUrl         TestRail server URL, for example {@code https://tms.example.org}; requests go to
 *                        {@code <baseUrl>/index.php?/api/v2/...}
 * @param user            user name
 * @param password        password or API key; never printed
 * @param projectId       project the rules check against
 * @param connectTimeout  limit for opening a connection
 * @param readTimeout     limit for one response, further capped by {@code budget}
 * @param parallelism     number of requests the index loader runs at once
 * @param budget          limit for a whole index load
 * @param lookupThreshold number of unresolved ids above which the loader pages whole suites instead of looking up
 *                        cases one by one
 */
record TestRailConnection(
        String baseUrl,
        String user,
        String password,
        int projectId,
        Duration connectTimeout,
        Duration readTimeout,
        int parallelism,
        Duration budget,
        int lookupThreshold) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    private static final int DEFAULT_PARALLELISM = 8;
    private static final Duration DEFAULT_BUDGET = Duration.ofSeconds(60);
    private static final int DEFAULT_LOOKUP_THRESHOLD = 50;
    private static final String NOT_HTTP = "must be an absolute http or https URL";

    TestRailConnection {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(readTimeout, "readTimeout");
        Objects.requireNonNull(budget, "budget");
        requireHttpUrl(baseUrl);
        if (projectId <= 0) {
            throw new IllegalArgumentException("projectId must be positive: " + projectId);
        }
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive: " + parallelism);
        }
        if (lookupThreshold < 0) {
            throw new IllegalArgumentException("lookupThreshold must not be negative: " + lookupThreshold);
        }
        requireWholeMillis("connectTimeout", connectTimeout);
        requireWholeMillis("readTimeout", readTimeout);
        if (budget.isZero() || budget.isNegative()) {
            throw new IllegalArgumentException("budget must be positive: " + budget);
        }
    }

    /**
     * Creates a connection with a 10 s connect timeout, a 30 s read timeout, parallelism 8, a 60 s budget and a
     * lookup threshold of 50.
     */
    static TestRailConnection of(String baseUrl, String user, String password, int projectId) {
        return new TestRailConnection(
                baseUrl,
                user,
                password,
                projectId,
                DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_READ_TIMEOUT,
                DEFAULT_PARALLELISM,
                DEFAULT_BUDGET,
                DEFAULT_LOOKUP_THRESHOLD);
    }

    /** Limit for one response: the read timeout, but never more than the whole budget. */
    Duration requestTimeout() {
        return readTimeout.compareTo(budget) <= 0 ? readTimeout : budget;
    }

    @Override
    public String toString() {
        return ("TestRailConnection[baseUrl=%s, user=%s, password=***, projectId=%d, connectTimeout=%s, "
                        + "readTimeout=%s, parallelism=%d, budget=%s, lookupThreshold=%d]")
                .formatted(baseUrl, user, projectId, connectTimeout, readTimeout, parallelism, budget, lookupThreshold);
    }

    private static void requireHttpUrl(String baseUrl) {
        urlProblem(baseUrl).ifPresent(problem -> {
            throw new IllegalArgumentException("baseUrl " + problem);
        });
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
