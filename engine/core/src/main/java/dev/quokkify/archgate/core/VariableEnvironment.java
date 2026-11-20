package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Environment;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * An {@link Environment} backed by a name lookup, such as system properties then environment variables.
 *
 * <p>Rules run on parallel threads, so {@link #wasRead()} is thread-safe.
 */
public final class VariableEnvironment implements Environment {

    private static final String BAD_NAME = "Variable names must not be null or blank";

    private final Function<String, String> lookup;
    private final boolean offline;
    private final AtomicBoolean read = new AtomicBoolean();

    /**
     * Creates the environment.
     *
     * @param lookup returns the value of a name, or null when unset
     * @param offline whether {@link #require} must refuse to run
     */
    public VariableEnvironment(Function<String, String> lookup, boolean offline) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.offline = offline;
    }

    /**
     * Tells whether a rule asked for any variable.
     *
     * @return true once {@code require} or {@code optional} was called, even if the call then threw
     */
    public boolean wasRead() {
        return read.get();
    }

    @Override
    public Map<String, String> require(String... names) {
        for (String name : names) {
            validate(name);
        }
        read.set(true);
        if (offline) {
            throw ExternalDependencyUnavailable.offline();
        }
        Map<String, String> values = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (String name : names) {
            String value = lookup.apply(name);
            if (Objects.isNull(value) || value.isBlank()) {
                missing.add(name);
            } else {
                values.put(name, value);
            }
        }
        if (!missing.isEmpty()) {
            throw ExternalDependencyUnavailable.missingVariables(missing);
        }
        return values;
    }

    @Override
    public Optional<String> optional(String name) {
        validate(name);
        read.set(true);
        String value = lookup.apply(name);
        return Objects.isNull(value) || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static void validate(String name) {
        if (Objects.isNull(name) || name.isBlank()) {
            throw new IllegalArgumentException(BAD_NAME);
        }
    }
}
