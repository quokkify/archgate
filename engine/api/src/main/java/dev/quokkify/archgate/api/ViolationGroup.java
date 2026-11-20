package dev.quokkify.archgate.api;

import java.util.List;
import java.util.Objects;

/**
 * A named set of violations that share one reason, for {@link ArchitectureViolation#grouped}.
 *
 * @param title the reason the violations share; must not be {@code null}
 * @param violations one entry per violation; copied, so later changes to the source list have no effect
 */
public record ViolationGroup(String title, List<String> violations) {

    /**
     * Creates a group and copies the violations.
     *
     * @param title the reason the violations share; must not be {@code null}
     * @param violations one entry per violation
     * @throws NullPointerException if {@code title} or {@code violations} is {@code null}, or a violation is
     *     {@code null}
     */
    public ViolationGroup {
        Objects.requireNonNull(title, "title");
        violations = List.copyOf(violations);
    }
}
