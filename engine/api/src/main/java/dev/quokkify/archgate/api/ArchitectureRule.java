package dev.quokkify.archgate.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A single architecture check. Consumers register rules in
 * {@code META-INF/services/dev.quokkify.archgate.api.ArchitectureRule}; an implementation needs a public
 * no-arg constructor.
 *
 * <p>All rules run concurrently, one virtual thread per rule, and share one context instance per context
 * type. Keep per-run state in local variables, not in fields of the rule.
 *
 * <p>Outcomes of {@link #verify}: throwing {@link ArchitectureViolation} or {@link AssertionError} reports
 * a finding, and {@link #severity()} decides whether it fails the build under the configured {@code
 * failOn}. Throwing {@link UnableToVerifyError}, or any other {@link Throwable}, means the check could not
 * run; that always fails the build regardless of {@code failOn}. A rule that checked nothing must not
 * return silently but throw {@link UnableToVerifyError}.
 *
 * @param <C> the context type the rule needs
 */
public interface ArchitectureRule<C extends AnalysisContext> {

    /**
     * Returns the rule name shown in the report.
     *
     * @return a human-readable name
     */
    String name();

    /**
     * Returns the severity of this rule's findings, which decides whether they fail the build under the
     * configured {@code failOn}.
     *
     * @return the severity of a finding
     */
    Severity severity();

    /**
     * Returns the scopes the rule applies to.
     *
     * @return {@link Scope#MAIN} and {@link Scope#TEST} by default
     */
    default Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN, Scope.TEST);
    }

    /**
     * Returns the context type the rule asks for. The first rule that asks for a type makes core call the
     * {@link ContextProvider} registered for it.
     *
     * @return the context class
     */
    Class<C> contextType();

    /**
     * Runs the check, once per run, possibly concurrently with other rules on the same shared context.
     *
     * @param context the shared context of type {@link #contextType()}; must be treated as thread-safe
     *     and read-only
     * @throws ArchitectureViolation to report a finding, usually via {@link #checkViolations}
     * @throws UnableToVerifyError if the rule cannot give a trustworthy answer
     */
    void verify(C context);

    /**
     * Tells whether every scope of {@link #scopes()} must have something to analyse. By default a rule over
     * several scopes checks the non-empty ones and fails only when all are empty. A subclass that returns
     * {@code true} restores the strict behaviour: an empty view in any scope fails the build as unable to
     * verify.
     *
     * @return {@code false} by default
     */
    default boolean requireAllScopes() {
        return false;
    }

    /**
     * Returns the scopes this rule reads in a run: the non-empty scopes of {@link #scopes()} as chosen by
     * {@link AnalysisContext#scopesToCheck(Set)}, or all of them in {@link Scope} order when {@link
     * #requireAllScopes()} is {@code true}, so that reading an empty view fails closed.
     *
     * @param context the context passed to {@link #verify}
     * @return the scopes to read
     * @throws UnableToVerifyError if there is nothing to check
     */
    default List<Scope> scopesToCheck(C context) {
        if (requireAllScopes()) {
            return scopes().stream().sorted().toList();
        }
        return context.scopesToCheck(scopes());
    }

    /**
     * Returns the scopes of {@link #scopes()} that the last {@link #verify} did not check because their view
     * was empty. Core calls it after {@link #verify} returned or reported a finding, and shows the result
     * on the rule's report line so that a partial check is visible.
     *
     * <p>The default asks the context with {@link AnalysisContext#isEmpty(Scope)}, which matches rules that
     * read their views through {@link #scopesToCheck(AnalysisContext)}. Override it only when the rule
     * decides what to read in another way.
     *
     * @param context the context passed to {@link #verify}
     * @return the skipped scopes; empty when every scope was checked
     */
    default Set<Scope> skippedScopes(C context) {
        return scopes().stream()
                .filter(context::isEmpty)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Scope.class)));
    }

    /**
     * Throws {@link ArchitectureViolation} if {@code violations} is not empty; does nothing otherwise.
     *
     * @param expectedContract description of what the code is expected to satisfy, used as the message
     *     header
     * @param violations one entry per violation found
     * @throws ArchitectureViolation if {@code violations} is not empty
     */
    default void checkViolations(String expectedContract, List<String> violations) {
        if (!violations.isEmpty()) {
            throw new ArchitectureViolation(expectedContract, violations);
        }
    }
}
