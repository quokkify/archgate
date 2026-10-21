package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ReportFormatter {

    private static final String SEPARATOR = "=".repeat(60);
    private static final String CONTINUATION_INDENT = "    ";
    private static final String LINE_FORMAT = "%-7s %-9s %6d ms  %s";
    private static final String POLICY_ROW_FORMAT = "  %-8s %s";
    private static final String SCOPE_SEPARATOR = "+";
    private static final String NEVER = "NEVER";
    private static final List<Severity> DISPLAY_ORDER = List.of(Severity.ERROR, Severity.WARNING, Severity.INFO);

    private ReportFormatter() {}

    public static List<String> format(GateResult result, Optional<Severity> failOn) {
        List<String> lines = new ArrayList<>();
        lines.add(SEPARATOR);
        lines.add("Architecture verification (%s, %s total)"
                .formatted(rulesPhrase(result.outcomes().size()), total(result)));
        lines.add(SEPARATOR);
        result.outcomes().stream()
                .filter(outcome -> Objects.isNull(outcome.finding()))
                .forEach(outcome -> lines.add(row("[PASS]", outcome)));
        result.outcomes().stream()
                .filter(outcome -> Objects.nonNull(outcome.finding()))
                .forEach(outcome -> {
                    lines.add(row("[%s]".formatted(outcome.rule().severity().name()), outcome));
                    describe(outcome.finding())
                            .lines()
                            .map(CONTINUATION_INDENT::concat)
                            .forEach(lines::add);
                });
        if (!result.modelBuildTimes().isEmpty()) {
            lines.add("Model build: "
                    + result.modelBuildTimes().entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(time -> "%s %d ms"
                                    .formatted(time.getKey(), time.getValue().toMillis()))
                            .collect(Collectors.joining(", ")));
        }
        lines.add(SEPARATOR);
        lines.add("Gate %s: %s".formatted(result.failsOn(failOn) ? "FAILED" : "PASSED", counts(result)));
        addFailPolicy(lines, failOn);
        return List.copyOf(lines);
    }

    public static String counts(GateResult result) {
        String findings = DISPLAY_ORDER.stream()
                .filter(severity -> result.count(severity) > 0)
                .map(severity -> "%d %s".formatted(result.count(severity), label(result.count(severity), severity)))
                .collect(Collectors.joining(", "));
        return "%s, %s".formatted(rulesPhrase(result.outcomes().size()), findings.isEmpty() ? "no findings" : findings);
    }

    private static void addFailPolicy(List<String> lines, Optional<Severity> failOn) {
        if (failOn.isEmpty()) {
            lines.add("Failure policy (failOn=%s): findings are reported, the build never fails".formatted(NEVER));
            return;
        }
        lines.add("Failure policy (failOn=%s):".formatted(failOn.get()));
        DISPLAY_ORDER.forEach(severity -> lines.add(POLICY_ROW_FORMAT.formatted(
                severity.name(), severity.compareTo(failOn.get()) >= 0 ? "fails the build" : "reported only")));
    }

    private static String label(long count, Severity severity) {
        return switch (severity) {
            case ERROR -> count == 1 ? "error" : "errors";
            case WARNING -> count == 1 ? "warning" : "warnings";
            case INFO -> "info";
        };
    }

    private static String rulesPhrase(long rules) {
        return rules == 1 ? "1 rule" : rules + " rules";
    }

    private static String total(GateResult result) {
        long millis = result.elapsedMillis();
        return millis < 1000 ? millis + " ms" : String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }

    private static String row(String label, RuleOutcome outcome) {
        String scope =
                outcome.rule().scopes().stream().sorted().map(Enum::name).collect(Collectors.joining(SCOPE_SEPARATOR));
        return LINE_FORMAT.formatted(
                label, scope, outcome.duration().toMillis(), outcome.rule().name());
    }

    private static String describe(Throwable finding) {
        String message = finding.getMessage();
        if (Objects.isNull(message) || message.isBlank()) {
            return "%s was thrown without a message"
                    .formatted(finding.getClass().getName());
        }
        return message;
    }
}
