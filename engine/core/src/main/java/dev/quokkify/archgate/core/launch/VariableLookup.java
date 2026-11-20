package dev.quokkify.archgate.core.launch;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Builds the lookup a plugin passes in {@link LaunchRequest#lookup()}. */
public final class VariableLookup {

    private VariableLookup() {}

    /**
     * Returns a lookup that asks each source in order and returns the first non-blank value.
     *
     * <p>Sources are asked on every lookup, not when the lookup is built.
     *
     * @param sources the sources in priority order, for example system properties then environment variables
     * @return the lookup; it returns null when every source has no value or a blank one
     */
    public static Function<String, String> firstNonBlank(List<Function<String, String>> sources) {
        List<Function<String, String>> ordered = List.copyOf(sources);
        return name -> ordered.stream()
                .map(source -> source.apply(name))
                .filter(value -> Objects.nonNull(value) && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
