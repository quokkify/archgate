package dev.quokkify.archgate.gradle;

import java.util.Objects;

final class FailureMessage {

    private FailureMessage() {}

    static String describe(Throwable failure) {
        Throwable cause = failure.getCause();
        if (Objects.isNull(cause)) {
            return failure.getMessage();
        }
        String detail = Objects.isNull(cause.getMessage()) ? "" : ": " + cause.getMessage();
        return failure.getMessage() + System.lineSeparator() + "Caused by: "
                + cause.getClass().getName() + detail;
    }
}
