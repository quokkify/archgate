package dev.quokkify.archgate.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Thrown by a rule to report a finding: the code under analysis breaks the rule's contract. An {@link
 * AssertionError} thrown from a rule is reported as a finding too. Whether a finding fails the build is
 * decided by the rule's {@link ArchitectureRule#severity()} and the configured {@code failOn}.
 *
 * <p>Rules usually throw it through {@link ArchitectureRule#checkViolations} or {@link
 * ArchitectureRule#checkViolationGroups}.
 */
public final class ArchitectureViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final String TABLE_INDENT = "    ";
    private static final String CELL_SEPARATOR = "  ";

    private final int count;

    /**
     * Creates a violation whose message lists the expected contract and each violation.
     *
     * @param expectedContract description of what the code is expected to satisfy
     * @param violations one entry per violation found
     */
    public ArchitectureViolation(String expectedContract, Collection<String> violations) {
        this(flat(expectedContract, violations), violations.size());
    }

    private ArchitectureViolation(String message, int count) {
        super(message);
        this.count = count;
    }

    /**
     * Creates a violation whose message lists the expected contract, then each non-empty group under its
     * title. Empty groups are left out, and an empty line separates consecutive groups. A table group prints its
     * header row, a dashes line and one aligned line per row instead of bulleted lines.
     *
     * @param expectedContract description of what the code is expected to satisfy
     * @param groups the violations, grouped by reason
     * @return the violation to throw
     */
    public static ArchitectureViolation grouped(String expectedContract, List<ViolationGroup> groups) {
        StringBuilder message = new StringBuilder(expectedContract);
        boolean first = true;
        for (ViolationGroup group : groups) {
            if (group.violations().isEmpty()) {
                continue;
            }
            if (!first) {
                message.append(System.lineSeparator());
            }
            first = false;
            message.append(System.lineSeparator())
                    .append("Violations (")
                    .append(group.violations().size())
                    .append(") \"")
                    .append(group.title())
                    .append("\":");
            if (group.isTable()) {
                appendTable(message, group);
            } else {
                appendLines(message, group.violations());
            }
        }
        int count = groups.stream().mapToInt(group -> group.violations().size()).sum();
        return new ArchitectureViolation(message.toString(), count);
    }

    /**
     * Returns the number of violations: the entries of a flat violation, or the entries and rows of all groups
     * together.
     *
     * @return the number of violations
     */
    public int count() {
        return count;
    }

    private static String flat(String expectedContract, Collection<String> violations) {
        StringBuilder message = new StringBuilder(expectedContract);
        message.append(System.lineSeparator())
                .append("Violations (")
                .append(violations.size())
                .append("):");
        appendLines(message, violations);
        return message.toString();
    }

    private static void appendTable(StringBuilder message, ViolationGroup group) {
        int[] widths = group.columns().stream().mapToInt(String::length).toArray();
        group.rows().forEach(row -> {
            for (int column = 0; column < widths.length; column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        });
        appendTableLine(message, group.columns(), widths);
        appendTableLine(message, Arrays.stream(widths).mapToObj("-"::repeat).toList(), widths);
        group.rows().forEach(row -> appendTableLine(message, row, widths));
    }

    private static void appendTableLine(StringBuilder message, List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder(TABLE_INDENT);
        for (int column = 0; column < widths.length; column++) {
            if (column > 0) {
                line.append(CELL_SEPARATOR);
            }
            line.append(String.format("%-" + widths[column] + "s", cells.get(column)));
        }
        message.append(System.lineSeparator()).append(line.toString().stripTrailing());
    }

    private static void appendLines(StringBuilder message, Collection<String> violations) {
        violations.forEach(violation ->
                message.append(System.lineSeparator()).append("    - ").append(violation));
    }
}
