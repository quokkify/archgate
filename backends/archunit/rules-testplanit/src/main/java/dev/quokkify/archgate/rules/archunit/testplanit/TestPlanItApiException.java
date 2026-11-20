package dev.quokkify.archgate.rules.archunit.testplanit;

/** TestPlanIt answered a request with a status outside 2xx. The message names the request, never the token. */
final class TestPlanItApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    TestPlanItApiException(String request, int status) {
        super("TestPlanIt %s returned %d".formatted(request, status));
        this.status = status;
    }

    int status() {
        return status;
    }
}
