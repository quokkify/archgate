package dev.quokkify.archgate.gradle;

import java.util.Objects;

/** Reads a run variable from system properties, then from the environment. */
final class RunLookup {

    private RunLookup() {}

    /**
     * Returns the first non-blank value of a name.
     *
     * @param name the property or environment variable name
     * @return the value, or null when both are unset or blank
     */
    static String lookup(String name) {
        String property = System.getProperty(name);
        if (Objects.nonNull(property) && !property.isBlank()) {
            return property;
        }
        String variable = System.getenv(name);
        return Objects.nonNull(variable) && !variable.isBlank() ? variable : null;
    }
}
