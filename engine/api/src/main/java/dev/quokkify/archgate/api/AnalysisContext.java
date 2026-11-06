package dev.quokkify.archgate.api;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Tells whether the view of {@code scope} has nothing to analyse under the root package, so that
     * reading it would throw {@link UnableToVerifyError}.
     *
     * <p>A backend that cannot tell without failing keeps the default, and its views keep failing closed
     * on their own.
     *
     * @param scope the scope to look at
     * @return {@code true} if the view of {@code scope} is empty; {@code false} by default
     */
    default boolean isEmpty(Scope scope) {
        return false;
    }

    /**
     * Returns the scopes among {@code scopes} that have something to analyse, in {@link Scope} order.
     *
     * <p>Rules should not call this directly but {@link ArchitectureRule#scopesToCheck(AnalysisContext)},
     * which delegates here unless the rule's {@link ArchitectureRule#requireAllScopes()} asks for every
     * scope.
     *
     * @param scopes the scopes the rule applies to, usually {@link ArchitectureRule#scopes()}
     * @return the non-empty scopes among {@code scopes}
     * @throws UnableToVerifyError if {@code scopes} is empty, or if every scope in it is empty, because the
     *     rule would otherwise pass without checking anything
     */
    default List<Scope> nonEmptyScopes(Set<Scope> scopes) {
        if (scopes.isEmpty()) {
            throw new UnableToVerifyError(
                    "The rule declares no scopes, so it has nothing to check. Return at least one scope from scopes().");
        }
        List<Scope> present =
                scopes.stream().sorted().filter(scope -> !isEmpty(scope)).toList();
        if (present.isEmpty()) {
            String names = scopes.stream()
                    .sorted()
                    .map(scope -> scope.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" and "));
            throw new UnableToVerifyError("""
          Every view this rule reads (%s) is empty, so the rule would pass without checking anything. Build the \
          modules first and check the class and source directories passed to the verification.""".formatted(names));
        }
        return present;
    }
}
