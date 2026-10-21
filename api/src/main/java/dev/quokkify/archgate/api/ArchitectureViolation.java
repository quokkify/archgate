package dev.quokkify.archgate.api;

import java.util.Collection;

public final class ArchitectureViolation extends RuntimeException {

    private static final long serialVersionUID = 1L;

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
