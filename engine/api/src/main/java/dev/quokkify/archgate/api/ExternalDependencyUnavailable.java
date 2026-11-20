package dev.quokkify.archgate.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Thrown by a rule when something it needs from outside the build is not available: variables that were
 * not passed, the switch that turns external rules off, or a service that does not answer.
 *
 * <p>Outside CI the rule is skipped and reported as {@code [SKIP]}. In CI the build fails, except when the
 * service is unavailable and {@code archgate.ci.serviceUnavailable=skip} is set. Create instances with
 * the factory methods; they carry the messages the report prints.
 */
public final class ExternalDependencyUnavailable extends Error {

    private static final long serialVersionUID = 1L;

    /**
     * Why the external dependency is unavailable.
     */
    public enum Reason {
        /** One or more required variables are unset or blank. */
        MISSING_VARIABLES,
        /** External rules are switched off by {@code archgate.offline=true}. */
        OFFLINE,
        /** The service did not answer or failed. */
        SERVICE_UNAVAILABLE
    }

    private final Reason reason;
    private final ArrayList<String> missingVariables;
    private final String service;

    private ExternalDependencyUnavailable(
            String message, Throwable cause, Reason reason, List<String> missingVariables, String service) {
        super(message, cause);
        this.reason = reason;
        this.missingVariables = new ArrayList<>(missingVariables);
        this.service = service;
    }

    /**
     * Creates the error for variables that are unset or blank.
     *
     * @param names every missing variable name, not empty
     * @return the error, with reason {@link Reason#MISSING_VARIABLES}
     * @throws IllegalArgumentException if {@code names} is empty
     */
    public static ExternalDependencyUnavailable missingVariables(List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("At least one missing variable name is required.");
        }
        String message = "Missing %s. Pass them as -DNAME=value or as environment variables."
                .formatted(String.join(", ", names));
        return new ExternalDependencyUnavailable(message, null, Reason.MISSING_VARIABLES, names, null);
    }

    /**
     * Creates the error for external rules that are switched off.
     *
     * @return the error, with reason {@link Reason#OFFLINE}
     */
    public static ExternalDependencyUnavailable offline() {
        return new ExternalDependencyUnavailable(
                "External rules are switched off by archgate.offline=true.", null, Reason.OFFLINE, List.of(), null);
    }

    /**
     * Creates the error for a service that did not answer.
     *
     * @param service the service name used in the message, for example {@code Tracker}
     * @param cause what went wrong; its message, or its class name when it has none, goes into the message
     * @return the error, with reason {@link Reason#SERVICE_UNAVAILABLE}
     */
    public static ExternalDependencyUnavailable serviceUnavailable(String service, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        String detail = Objects.nonNull(cause.getMessage())
                ? cause.getMessage()
                : cause.getClass().getName();
        return new ExternalDependencyUnavailable(
                "%s is unavailable: %s".formatted(service, detail),
                cause,
                Reason.SERVICE_UNAVAILABLE,
                List.of(),
                service);
    }

    /**
     * Returns why the dependency is unavailable.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Returns the missing variable names.
     *
     * @return the names for {@link Reason#MISSING_VARIABLES}; empty otherwise
     */
    public List<String> missingVariables() {
        return List.copyOf(missingVariables);
    }

    /**
     * Returns the name of the unavailable service.
     *
     * @return the service for {@link Reason#SERVICE_UNAVAILABLE}; empty otherwise
     */
    public Optional<String> service() {
        return Optional.ofNullable(service);
    }
}
