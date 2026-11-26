package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.Severity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ReportFormatter {

    private static final String SEPARATOR = "=".repeat(60);
    private static final String CONTINUATION_INDENT = "    ";
    private static final String LINE_FORMAT = "%-7s %-9s %6d ms  %s";
    private static final String POLICY_ROW_FORMAT = "  %-8s %s";
    private static final String SCOPE_SEPARATOR = "+";
    private static final String NEVER = "NEVER";
    private static final List<Severity> DISPLAY_ORDER = List.of(Severity.ERROR, Severity.WARNING, Severity.INFO);
    private static final List<String> TABLE_HEADER =
            List.of("RULE", "SCOPE", "SEVERITY", "RESULT", "FOUND", "BUILD", "TIME");
    private static final int FOUND_COLUMN = 4;
    private static final int TIME_COLUMN = 6;
    private static final Set<Integer> RIGHT_ALIGNED_COLUMNS = Set.of(FOUND_COLUMN, TIME_COLUMN);
    private static final String COLUMN_GAP = "  ";
    private static final String NO_VALUE = "-";
    private static final String TABLE_END = "-".repeat(60);
    private static final String TIME_FORMAT = "%6d ms";

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
        return format(result, failOn, ReportStyle.CLASSIC);
    }

    /**
     * Formats a gate run as report blocks in the given style. In {@link ReportStyle#TABLE} every line of the summary
     * table is a block of its own, so each line gets the same log prefix and the columns stay aligned; the details of
     * skipped and violated rules follow the table, each block headed by the rule name.
     *
     * @param result the outcome of the run
     * @param failOn the failure threshold, empty when the build never fails
     * @param style the report style
     * @return the report blocks, in order
     */
    public static List<String> format(GateResult result, Optional<Severity> failOn, ReportStyle style) {
        return format(result, failOn, style, System.lineSeparator());
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
        return format(result, failOn, ReportStyle.CLASSIC, lineSeparator);
    }

    static List<String> format(GateResult result, Optional<Severity> failOn, ReportStyle style, String lineSeparator) {
        List<String> lines = new ArrayList<>();
        lines.add(SEPARATOR);
        lines.add("Architecture verification (%s, %s total)"
                .formatted(rulesPhrase(result.outcomes().size()), total(result)));
        lines.add(SEPARATOR);
        if (style == ReportStyle.TABLE) {
            addTableRules(lines, result, failOn, lineSeparator);
        } else {
            addClassicRules(lines, result, lineSeparator);
        }
        addTimes(lines, "Model build: ", result.modelBuildTimes());
        addTimes(lines, "Shared: ", result.sharedBuildTimes());
        lines.add(SEPARATOR);
        lines.add("Gate %s: %s".formatted(result.failsOn(failOn) ? "FAILED" : "PASSED", counts(result)));
        addFailPolicy(lines, failOn);
        return List.copyOf(lines);
    }

    private static void addClassicRules(List<String> lines, GateResult result, String lineSeparator) {
        passed(result).forEach(outcome -> lines.add(row("[PASS]", outcome)));
        skipped(result)
                .forEach(
                        outcome -> lines.add(withDetails(lineSeparator, row("[SKIP]", outcome), skipDetails(outcome))));
        findings(result)
                .forEach(outcome -> lines.add(withDetails(
                        lineSeparator,
                        row("[%s]".formatted(outcome.rule().severity().name()), outcome),
                        findingDetails(outcome))));
    }

    private static void addTableRules(
            List<String> lines, GateResult result, Optional<Severity> failOn, String lineSeparator) {
        List<List<String>> rows = new ArrayList<>();
        passed(result).forEach(outcome -> rows.add(tableRow(outcome, "passed", NO_VALUE, NO_VALUE)));
        skipped(result).forEach(outcome -> rows.add(tableRow(outcome, "skipped", NO_VALUE, NO_VALUE)));
        findings(result).forEach(outcome -> rows.add(tableRow(outcome, "violated", NO_VALUE, build(outcome, failOn))));
        int[] widths = columnWidths(rows);
        lines.add(tableLine(TABLE_HEADER, widths));
        lines.add(Arrays.stream(widths).mapToObj(NO_VALUE::repeat).collect(Collectors.joining(COLUMN_GAP)));
        rows.forEach(row -> lines.add(tableLine(row, widths)));
        lines.add(TABLE_END);
        skipped(result)
                .forEach(outcome ->
                        lines.add(withDetails(lineSeparator, outcome.rule().name(), skipDetails(outcome))));
        findings(result)
                .forEach(outcome ->
                        lines.add(withDetails(lineSeparator, outcome.rule().name(), findingDetails(outcome))));
    }

    private static List<String> tableRow(RuleOutcome outcome, String status, String found, String build) {
        return List.of(
                outcome.rule().name() + skippedNote(outcome),
                scope(outcome),
                outcome.rule().severity().name(),
                status,
                found,
                build,
                TIME_FORMAT.formatted(outcome.duration().toMillis()));
    }

    private static String build(RuleOutcome outcome, Optional<Severity> failOn) {
        Severity severity = outcome.rule().severity();
        return failOn.filter(threshold -> severity.compareTo(threshold) >= 0).isPresent() ? "fails" : "report";
    }

    private static int[] columnWidths(List<List<String>> rows) {
        int[] widths = TABLE_HEADER.stream().mapToInt(String::length).toArray();
        rows.forEach(row -> {
            for (int column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        });
        return widths;
    }

    private static String tableLine(List<String> cells, int[] widths) {
        List<String> padded = new ArrayList<>();
        for (int column = 0; column < widths.length; column++) {
            String alignment = RIGHT_ALIGNED_COLUMNS.contains(column) ? "" : "-";
            padded.add(("%" + alignment + widths[column] + "s").formatted(cells.get(column)));
        }
        return String.join(COLUMN_GAP, padded).stripTrailing();
    }

    private static Stream<RuleOutcome> passed(GateResult result) {
        return result.outcomes().stream()
                .filter(outcome -> Objects.isNull(outcome.finding()) && !outcome.externallySkipped());
    }

    private static Stream<RuleOutcome> skipped(GateResult result) {
        return result.outcomes().stream().filter(RuleOutcome::externallySkipped);
    }

    private static Stream<RuleOutcome> findings(GateResult result) {
        return result.outcomes().stream().filter(outcome -> Objects.nonNull(outcome.finding()));
    }

    private static Stream<String> skipDetails(RuleOutcome outcome) {
        return Stream.of(
                outcome.externalSkip().message(), outcome.externalSkip().reason());
    }

    private static Stream<String> findingDetails(RuleOutcome outcome) {
        return describe(outcome.finding()).lines();
    }

    private static String withDetails(String lineSeparator, String head, Stream<String> details) {
        return Stream.concat(Stream.of(head), details.map(CONTINUATION_INDENT::concat))
                .collect(Collectors.joining(lineSeparator));
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
        return LINE_FORMAT.formatted(
                        label,
                        scope(outcome),
                        outcome.duration().toMillis(),
                        outcome.rule().name())
                + skippedNote(outcome);
    }

    private static String scope(RuleOutcome outcome) {
        return outcome.rule().scopes().stream().sorted().map(Enum::name).collect(Collectors.joining(SCOPE_SEPARATOR));
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
