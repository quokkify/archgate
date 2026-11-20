package dev.quokkify.archgate.api;

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

    /**
     * Creates a violation whose message lists the expected contract and each violation.
     *
     * @param expectedContract description of what the code is expected to satisfy
     * @param violations one entry per violation found
     */
    public ArchitectureViolation(String expectedContract, Collection<String> violations) {
        this(flat(expectedContract, violations));
    }

    private ArchitectureViolation(String message) {
        super(message);
    }

    /**
     * Creates a violation whose message lists the expected contract, then each non-empty group under its
     * title. Empty groups are left out.
     *
     * @param expectedContract description of what the code is expected to satisfy
     * @param groups the violations, grouped by reason
     * @return the violation to throw
     */
    public static ArchitectureViolation grouped(String expectedContract, List<ViolationGroup> groups) {
        StringBuilder message = new StringBuilder(expectedContract);
        for (ViolationGroup group : groups) {
            if (group.violations().isEmpty()) {
                continue;
            }
            message.append(System.lineSeparator())
                    .append("Violations (")
                    .append(group.violations().size())
                    .append(") \"")
                    .append(group.title())
                    .append("\":");
            appendLines(message, group.violations());
        }
        return new ArchitectureViolation(message.toString());
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

    private static void appendLines(StringBuilder message, Collection<String> violations) {
        violations.forEach(violation ->
                message.append(System.lineSeparator()).append("    - ").append(violation));
    }
}
