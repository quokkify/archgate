package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import java.time.Duration;
import java.util.Set;

public record RuleOutcome(ArchitectureRule<?> rule, Throwable finding, Duration duration, Set<Scope> skipped) {

    public RuleOutcome {
        skipped = Set.copyOf(skipped);
    }

    public RuleOutcome(ArchitectureRule<?> rule, Throwable finding, Duration duration) {
        this(rule, finding, duration, Set.of());
    }
}
