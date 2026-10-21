package dev.quokkify.archgate.api;

public class UnableToVerifyError extends Error {

    private static final long serialVersionUID = 1L;

    public UnableToVerifyError(String message) {
        super(message);
    }

    public UnableToVerifyError(String message, Throwable cause) {
        super(message, cause);
    }
}
