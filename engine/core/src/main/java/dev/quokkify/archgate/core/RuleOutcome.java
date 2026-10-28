package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ArchitectureRule;
import java.time.Duration;

public record RuleOutcome(ArchitectureRule<?> rule, Throwable finding, Duration duration) {}
