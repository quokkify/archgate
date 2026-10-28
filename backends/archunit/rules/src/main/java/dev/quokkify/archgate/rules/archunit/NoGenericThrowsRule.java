package dev.quokkify.archgate.rules.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class NoGenericThrowsRule implements ArchitectureRule<ArchUnitContext> {

    private static final Set<String> GENERIC_THROWABLES =
            Set.of(Exception.class.getName(), RuntimeException.class.getName(), Throwable.class.getName());
    private static final String EXPECTED_CONTRACT = """
      Methods and constructors must not declare the generic exceptions Exception, RuntimeException or \
      Throwable. Declare the specific checked or unchecked exception the code can actually throw, so \
      callers handle the real failures precisely.""";

    @Override
    public String name() {
        return "No generic throws";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        List<String> violations = ArchRuleChecks.violations(definition(), context.main());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return ArchRuleDefinition.codeUnits()
                .that()
                .areDeclaredInClassesThat(DescribedPredicate.not(GeneratedCode.EXCLUDED))
                .should(notDeclareGenericThrowable())
                .allowEmptyShould(true)
                .because("a generic throws clause hides the real failure modes and forces blanket catching");
    }

    private static ArchCondition<JavaCodeUnit> notDeclareGenericThrowable() {
        return new ArchCondition<>("not declare a generic Exception, RuntimeException or Throwable") {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                codeUnit.getThrowsClause().getTypes().stream()
                        .filter(type -> GENERIC_THROWABLES.contains(type.getName()))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(
                                codeUnit,
                                "%s declares the generic exception %s"
                                        .formatted(codeUnit.getFullName(), type.getName()))));
            }
        };
    }
}
