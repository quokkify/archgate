package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * How a run treats rules that need something from outside the build.
 *
 * @param ci whether the run is a CI run
 * @param ciSource where the CI decision came from, for the report
 * @param offline whether external rules are switched off; true only outside CI
 * @param offlineIgnored whether {@code archgate.offline=true} was requested but ignored because of CI
 * @param serviceUnavailable what CI does when a service does not answer
 */
public record ExternalPolicy(
        boolean ci, String ciSource, boolean offline, boolean offlineIgnored, ServiceUnavailable serviceUnavailable) {

    /** What CI does when a service does not answer. */
    public enum ServiceUnavailable {
        /** The build fails. */
        FAIL,
        /** The rule is skipped. */
        SKIP
    }

    /** Property that overrides CI detection with {@code true} or {@code false}. */
    public static final String CI_PROPERTY = "archgate.ci";

    /** Property that switches external rules off outside CI. */
    public static final String OFFLINE_PROPERTY = "archgate.offline";

    /** Property that chooses what CI does when a service does not answer. */
    public static final String SERVICE_UNAVAILABLE_PROPERTY = "archgate.ci.serviceUnavailable";

    private static final String CI_VARIABLE = "CI";
    private static final String CI_FROM_VARIABLE = "CI is set";
    private static final String NO_CI = "no CI variable set";

    /**
     * Reads the policy from the given lookup.
     *
     * @param lookup returns the value of a name, or null when unset
     * @return the policy
     * @throws UnableToVerifyError if {@code archgate.ci} or {@code archgate.ci.serviceUnavailable} has an unknown value
     */
    public static ExternalPolicy detect(Function<String, String> lookup) {
        String override = trimmed(lookup.apply(CI_PROPERTY));
        boolean ci;
        String source;
        if (!override.isEmpty()) {
            ci = parseCi(override);
            source = CI_PROPERTY + "=" + override;
        } else {
            String variable = trimmed(lookup.apply(CI_VARIABLE));
            ci = !variable.isEmpty() && !"false".equalsIgnoreCase(variable);
            source = ci ? CI_FROM_VARIABLE : NO_CI;
        }
        boolean offlineRequested = "true".equalsIgnoreCase(trimmed(lookup.apply(OFFLINE_PROPERTY)));
        return new ExternalPolicy(
                ci,
                source,
                offlineRequested && !ci,
                offlineRequested && ci,
                parseServiceUnavailable(trimmed(lookup.apply(SERVICE_UNAVAILABLE_PROPERTY))));
    }

    /**
     * Tells whether a signal is reported as skipped even in CI.
     *
     * @param signal what the rule threw
     * @return true only in CI, for an unavailable service, when the policy says to skip
     */
    public boolean skipsInCi(ExternalDependencyUnavailable signal) {
        return ci
                && serviceUnavailable == ServiceUnavailable.SKIP
                && signal.reason() == ExternalDependencyUnavailable.Reason.SERVICE_UNAVAILABLE;
    }

    /**
     * Returns the warning for an {@code archgate.offline=true} that CI ignored.
     *
     * @return the warning, or empty when nothing was ignored
     */
    public Optional<String> offlineWarning() {
        if (!offlineIgnored) {
            return Optional.empty();
        }
        return Optional.of("%s=true is ignored in CI (%s); external rules run.".formatted(OFFLINE_PROPERTY, ciSource));
    }

    private static boolean parseCi(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new UnableToVerifyError("Unknown %s value '%s'. Use true or false.".formatted(CI_PROPERTY, value));
    }

    private static ServiceUnavailable parseServiceUnavailable(String value) {
        if (value.isEmpty()) {
            return ServiceUnavailable.FAIL;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "fail" -> ServiceUnavailable.FAIL;
            case "skip" -> ServiceUnavailable.SKIP;
            default ->
                throw new UnableToVerifyError(
                        "Unknown %s value '%s'. Use fail or skip.".formatted(SERVICE_UNAVAILABLE_PROPERTY, value));
        };
    }

    private static String trimmed(String value) {
        return Objects.isNull(value) ? "" : value.trim();
    }
}
