package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Severity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record GateResult(List<RuleOutcome> outcomes, Map<String, Duration> modelBuildTimes, long elapsedMillis) {

    public int count(Severity severity) {
        return (int) outcomes.stream()
                .filter(outcome ->
                        Objects.nonNull(outcome.finding()) && outcome.rule().severity() == severity)
                .count();
    }

    public Optional<Severity> worst() {
        return outcomes.stream()
                .filter(outcome -> Objects.nonNull(outcome.finding()))
                .map(outcome -> outcome.rule().severity())
                .max(Severity::compareTo);
    }

    public boolean failsOn(Optional<Severity> threshold) {
        return threshold.isPresent()
                && worst().filter(severity -> severity.compareTo(threshold.get()) >= 0)
                        .isPresent();
    }
}
