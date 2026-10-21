package dev.quokkify.archgate.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public interface ArchitectureRule<C extends AnalysisContext> {

    String name();

    Severity severity();

    default Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN, Scope.TEST);
    }

    Class<C> contextType();

    void verify(C context);

    default void checkViolations(String expectedContract, List<String> violations) {
        if (!violations.isEmpty()) {
            throw new ArchitectureViolation(expectedContract, violations);
        }
    }
}
