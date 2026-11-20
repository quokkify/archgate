package dev.quokkify.archgate.rules.archunit.testrail;

/** TestRail answered a request with a status that the client does not treat as data. */
final class TestRailApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String endpoint;
    private final int statusCode;

    TestRailApiException(String endpoint, int statusCode) {
        super("TestRail %s returned %d".formatted(endpoint, statusCode));
        this.endpoint = endpoint;
        this.statusCode = statusCode;
    }

    String endpoint() {
        return endpoint;
    }

    int statusCode() {
        return statusCode;
    }
}
