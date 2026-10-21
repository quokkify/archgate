package dev.quokkify.archgate.api;

/**
 * Thrown by a rule or a {@link ContextProvider} when the check cannot give a trustworthy answer, for
 * example because the classes it needs are missing or nothing was found to analyse. It always fails the
 * build, whatever {@code failOn} says, and no report is written. Any other {@link Throwable} that is not
 * an {@link ArchitectureViolation} or {@link AssertionError} is treated the same way.
 */
public class UnableToVerifyError extends Error {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the error with a message that says what is missing.
     *
     * @param message what prevented the check
     */
    public UnableToVerifyError(String message) {
        super(message);
    }

    /**
     * Creates the error with a message and the underlying cause.
     *
     * @param message what prevented the check
     * @param cause the underlying failure
     */
    public UnableToVerifyError(String message, Throwable cause) {
        super(message, cause);
    }
}
