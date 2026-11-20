package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Severity;
import java.time.Duration;
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

    /**
     * Formats a gate run as report blocks. A rule row and its detail lines form one block, joined by the platform
     * line separator; the header, footer, policy and timing lines are blocks of their own.
     *
     * @param result the outcome of the run
     * @param failOn the failure threshold, empty when the build never fails
     * @return the report blocks, in order
     */
    public static List<String> format(GateResult result, Optional<Severity> failOn) {
        return format(result, failOn, System.lineSeparator());
    }

    /**
     * Joins report blocks into the content of the report file, which uses LF only on every platform.
     *
     * @param blocks the blocks returned by {@link #format(GateResult, Optional)}
     * @return the file content
     */
    public static String fileContent(List<String> blocks) {
        return blocks.stream().map(block -> block.replace("\r\n", "\n")).collect(Collectors.joining("\n"));
    }

    static List<String> format(GateResult result, Optional<Severity> failOn, String lineSeparator) {
        List<String> lines = new ArrayList<>();
        lines.add(SEPARATOR);
        lines.add("Architecture verification (%s, %s total)"
                .formatted(rulesPhrase(result.outcomes().size()), total(result)));
        lines.add(SEPARATOR);
        result.outcomes().stream()
                .filter(outcome -> Objects.isNull(outcome.finding()) && !outcome.externallySkipped())
                .forEach(outcome -> lines.add(row("[PASS]", outcome)));
        result.outcomes().stream().filter(RuleOutcome::externallySkipped).forEach(outcome -> {
            lines.add(block(
                    lineSeparator,
                    row("[SKIP]", outcome),
                    CONTINUATION_INDENT + outcome.externalSkip().message(),
                    CONTINUATION_INDENT + outcome.externalSkip().reason()));
        });
        result.outcomes().stream()
                .filter(outcome -> Objects.nonNull(outcome.finding()))
                .forEach(outcome -> {
                    List<String> parts = new ArrayList<>();
                    parts.add(row("[%s]".formatted(outcome.rule().severity().name()), outcome));
                    describe(outcome.finding())
                            .lines()
                            .map(CONTINUATION_INDENT::concat)
                            .forEach(parts::add);
                    lines.add(String.join(lineSeparator, parts));
                });
        addTimes(lines, "Model build: ", result.modelBuildTimes());
        addTimes(lines, "Shared: ", result.sharedBuildTimes());
        lines.add(SEPARATOR);
        lines.add("Gate %s: %s".formatted(result.failsOn(failOn) ? "FAILED" : "PASSED", counts(result)));
        addFailPolicy(lines, failOn);
        return List.copyOf(lines);
    }

    private static String block(String lineSeparator, String... parts) {
        return String.join(lineSeparator, parts);
    }

    private static void addTimes(List<String> lines, String prefix, Map<String, Duration> times) {
        if (times.isEmpty()) {
            return;
        }
        lines.add(prefix
                + times.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(time -> "%s %d ms"
                                .formatted(time.getKey(), time.getValue().toMillis()))
                        .collect(Collectors.joining(", ")));
    }

    public static String counts(GateResult result) {
        String findings = DISPLAY_ORDER.stream()
                .filter(severity -> result.count(severity) > 0)
                .map(severity -> "%d %s".formatted(result.count(severity), label(result.count(severity), severity)))
                .collect(Collectors.joining(", "));
        long partial = result.outcomes().stream()
                .filter(outcome -> !outcome.skipped().isEmpty())
                .count();
        String rules = rulesPhrase(result.outcomes().size())
                + (partial == 0 ? "" : " (%d partially checked)".formatted(partial));
        int externallySkipped = result.externallySkipped();
        return "%s, %s%s"
                .formatted(
                        rules,
                        findings.isEmpty() ? "no findings" : findings,
                        externallySkipped == 0 ? "" : ", %d skipped".formatted(externallySkipped));
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
                        label,
                        scope,
                        outcome.duration().toMillis(),
                        outcome.rule().name())
                + skippedNote(outcome);
    }

    private static String skippedNote(RuleOutcome outcome) {
        if (outcome.skipped().isEmpty()) {
            return "";
        }
        String skipped =
                outcome.skipped().stream().sorted().map(Enum::name).collect(Collectors.joining(SCOPE_SEPARATOR));
        return " (%s not checked: empty)".formatted(skipped);
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
