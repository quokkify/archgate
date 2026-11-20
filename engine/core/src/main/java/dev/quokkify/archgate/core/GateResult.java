package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Severity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one gate run.
 *
 * @param outcomes the outcome of every rule, sorted by rule name
 * @param modelBuildTimes how long each analysis model took to build
 * @param sharedBuildTimes how long each shared value took to build, sorted by name
 * @param elapsedMillis how long the rules took altogether
 */
public record GateResult(
        List<RuleOutcome> outcomes,
        Map<String, Duration> modelBuildTimes,
        Map<String, Duration> sharedBuildTimes,
        long elapsedMillis) {

    /**
     * Creates a result of a run that shared no values.
     *
     * @param outcomes the outcome of every rule, sorted by rule name
     * @param modelBuildTimes how long each analysis model took to build
     * @param elapsedMillis how long the rules took altogether
     */
    public GateResult(List<RuleOutcome> outcomes, Map<String, Duration> modelBuildTimes, long elapsedMillis) {
        this(outcomes, modelBuildTimes, Map.of(), elapsedMillis);
    }

    public int count(Severity severity) {
        return (int) outcomes.stream()
                .filter(outcome ->
                        Objects.nonNull(outcome.finding()) && outcome.rule().severity() == severity)
                .count();
    }

    /**
     * Counts the rules that did not run because an outside dependency was unavailable.
     *
     * @return the number of skipped rules
     */
    public int externallySkipped() {
        return (int) outcomes.stream().filter(RuleOutcome::externallySkipped).count();
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
