package dev.quokkify.archgate.context.archunit.support;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArchRuleChecks {

    private ArchRuleChecks() {}

    public static List<String> violations(ArchRule rule, ArchUnitContext context, Set<Scope> scopes) {
        return violations(
                rule,
                context.scopesToCheck(scopes).stream().map(context::classes).toArray(JavaClasses[]::new));
    }

    public static List<String> violations(ArchRule rule, JavaClasses... classes) {
        List<String> violations = new ArrayList<>();
        for (JavaClasses set : classes) {
            EvaluationResult result = rule.evaluate(set);
            if (result.hasViolation()) {
                violations.addAll(result.getFailureReport().getDetails());
            }
        }
        return List.copyOf(violations);
    }
}
