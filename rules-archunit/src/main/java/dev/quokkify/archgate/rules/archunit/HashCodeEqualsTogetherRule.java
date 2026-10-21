package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.core.domain.JavaClass;
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

public class HashCodeEqualsTogetherRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      A class that overrides one of equals or hashCode must override both. Overriding only one breaks \
      the hash-based collection contract: objects that are equal can land in different buckets, and \
      lookups return wrong results.""";

    @Override
    public String name() {
        return "hashCode and equals together";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
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
        return ArchRuleDefinition.classes()
                .that(not(GeneratedCode.EXCLUDED))
                .should(overrideBothOrNeither())
                .allowEmptyShould(true)
                .because("equal objects with different hash codes break hash-based collections");
    }

    private static ArchCondition<JavaClass> overrideBothOrNeither() {
        return new ArchCondition<>("override both equals and hashCode, or neither") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean overridesEquals = javaClass.getMethods().stream()
                        .anyMatch(method -> "equals".equals(method.getName())
                                && method.getRawParameterTypes().size() == 1
                                && method.getRawParameterTypes()
                                        .get(0)
                                        .getName()
                                        .equals(Object.class.getName()));
                boolean overridesHashCode = javaClass.getMethods().stream()
                        .anyMatch(method -> "hashCode".equals(method.getName())
                                && method.getRawParameterTypes().isEmpty());
                if (overridesEquals != overridesHashCode) {
                    String missing = overridesEquals ? "hashCode" : "equals";
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            "%s overrides %s but not %s"
                                    .formatted(
                                            javaClass.getDescription(),
                                            overridesEquals ? "equals" : "hashCode",
                                            missing)));
                }
            }
        };
    }
}
