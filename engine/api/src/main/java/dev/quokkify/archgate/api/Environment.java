package dev.quokkify.archgate.api;

import java.util.Map;
import java.util.Optional;

/**
 * The variables a rule may read from outside the build: system properties and environment variables.
 *
 * <p>Unset, empty and blank values all count as missing.
 */
public interface Environment {

    /**
     * Returns the values of variables the rule cannot run without.
     *
     * @param names the variable names, none null or blank
     * @return the value of every name
     * @throws ExternalDependencyUnavailable if external rules are switched off, or if any name is missing;
     *     the error lists every missing name
     * @throws IllegalArgumentException if a name is null or blank
     */
    Map<String, String> require(String... names);

    /**
     * Returns the value of a variable the rule can run without.
     *
     * @param name the variable name, not null or blank
     * @return the value, or empty if unset or blank
     * @throws UnableToVerifyError by default, for an environment that provides no optional variables
     */
    default Optional<String> optional(String name) {
        throw new UnableToVerifyError(
                "This Environment provides no optional variables, so rules that read them cannot run with it.");
    }

    /**
     * Creates the error a rule throws when a service it calls does not answer.
     *
     * @param service the service name used in the message
     * @param cause what went wrong
     * @return the error to throw
     */
    default ExternalDependencyUnavailable unavailable(String service, Throwable cause) {
        return ExternalDependencyUnavailable.serviceUnavailable(service, cause);
    }
}
