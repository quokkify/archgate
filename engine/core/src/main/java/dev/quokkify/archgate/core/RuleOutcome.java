package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * What one rule produced in a run.
 *
 * @param rule the rule
 * @param finding the violation it threw, or null when it passed or was skipped
 * @param duration how long the rule took
 * @param skipped the scopes that were empty and so not checked
 * @param externalSkip why the rule did not run because an outside dependency was unavailable, or null
 */
public record RuleOutcome(
        ArchitectureRule<?> rule, Throwable finding, Duration duration, Set<Scope> skipped, ExternalSkip externalSkip) {

    public RuleOutcome {
        skipped = Set.copyOf(skipped);
    }

    public RuleOutcome(ArchitectureRule<?> rule, Throwable finding, Duration duration, Set<Scope> skipped) {
        this(rule, finding, duration, skipped, null);
    }

    public RuleOutcome(ArchitectureRule<?> rule, Throwable finding, Duration duration) {
        this(rule, finding, duration, Set.of(), null);
    }

    /**
     * Tells whether the rule did not run because an outside dependency was unavailable.
     *
     * @return true for a skip
     */
    public boolean externallySkipped() {
        return Objects.nonNull(externalSkip);
    }
}
