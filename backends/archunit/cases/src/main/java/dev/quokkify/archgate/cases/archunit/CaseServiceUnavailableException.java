package dev.quokkify.archgate.cases.archunit;

/**
 * Thrown by a {@link CaseService} when its system cannot answer. The rules turn it into the run's
 * service-unavailable signal, which skips them outside CI and fails the build in CI unless
 * {@code archgate.ci.serviceUnavailable=skip}.
 */
public class CaseServiceUnavailableException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what could not be done, shown in the report
     */
    public CaseServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * Creates the exception with its cause.
     *
     * @param message what could not be done, shown in the report
     * @param cause the underlying failure
     */
    public CaseServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
