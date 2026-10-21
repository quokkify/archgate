package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class FailOn {

    public static final Severity DEFAULT = Severity.WARNING;

    private static final String NEVER = "NEVER";

    private FailOn() {}

    public static Optional<Severity> parse(String value) {
        String trimmed = Objects.isNull(value) ? "" : value.trim();
        if (NEVER.equalsIgnoreCase(trimmed)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Severity.valueOf(trimmed.toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException unknown) {
            throw new UnableToVerifyError(
                    "Unknown failOn value '%s'. Use INFO, WARNING, ERROR or NEVER.".formatted(trimmed), unknown);
        }
    }
}
