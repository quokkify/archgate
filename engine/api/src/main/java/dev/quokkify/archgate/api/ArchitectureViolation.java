package dev.quokkify.archgate.api;

import java.util.Collection;

/**
 * Thrown by a rule to report a finding: the code under analysis breaks the rule's contract. An {@link
 * AssertionError} thrown from a rule is reported as a finding too. Whether a finding fails the build is
 * decided by the rule's {@link ArchitectureRule#severity()} and the configured {@code failOn}.
 *
 * <p>Rules usually throw it through {@link ArchitectureRule#checkViolations}.
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
        super(format(expectedContract, violations));
    }

    private static String format(String expectedContract, Collection<String> violations) {
        StringBuilder message = new StringBuilder(expectedContract.length() + violations.size() * 64);
        message.append(expectedContract)
                .append(System.lineSeparator())
                .append("Violations (")
                .append(violations.size())
                .append("):");
        violations.forEach(violation ->
                message.append(System.lineSeparator()).append("    - ").append(violation));
        return message.toString();
    }
}
