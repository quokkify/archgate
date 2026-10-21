package dev.quokkify.archgate.context.archunit.support;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.ArrayList;
import java.util.List;

public final class ArchRuleChecks {

    private ArchRuleChecks() {}

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
