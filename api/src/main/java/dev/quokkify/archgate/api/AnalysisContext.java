package dev.quokkify.archgate.api;

import java.time.Duration;
import java.util.Map;

/**
 * A view of the analysed project that rules query, built once per run by a {@link ContextProvider}.
 *
 * <p>The same instance is passed to every rule of its type, concurrently, so implementations must be
 * thread-safe. Build expensive views lazily and once. An implementation that holds resources should also
 * implement {@link AutoCloseable}; core closes it after all rules have finished, and rules must not close
 * it.
 */
public interface AnalysisContext {

    /**
     * Reports how long building the model took, for the {@code Model build:} line of the report.
     *
     * <p>Core merges the maps of all contexts into one map, so keys share a single namespace across
     * backends. Prefix keys with the backend name, for example {@code javaparser main}, so two backends
     * never overwrite each other's entries.
     *
     * @return build durations by key; empty by default
     */
    default Map<String, Duration> buildTimes() {
        return Map.of();
    }
}
