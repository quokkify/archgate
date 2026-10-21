package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

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

public class MaxMethodParametersRule implements ArchitectureRule<ArchUnitContext> {

    private static final int DEFAULT_MAX_PARAMETERS = 5;
    private static final String EXPECTED_CONTRACT = """
      Methods and constructors must not declare more than %d parameters. A long parameter list hides a \
      missing abstraction — group the related parameters into a type, or pass a builder.""";

    private final int maxParameters;

    public MaxMethodParametersRule() {
        this(DEFAULT_MAX_PARAMETERS);
    }

    protected MaxMethodParametersRule(int maxParameters) {
        this.maxParameters = maxParameters;
    }

    @Override
    public String name() {
        return "Max method parameters";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN, Scope.TEST);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        List<String> violations = ArchRuleChecks.violations(definition(maxParameters), context.main(), context.test());
        checkViolations(expectedContract(), violations);
    }

    static ArchRule definition() {
        return definition(DEFAULT_MAX_PARAMETERS);
    }

    static ArchRule definition(int maxParameters) {
        return ArchRuleDefinition.codeUnits()
                .that()
                .areDeclaredInClassesThat(not(GeneratedCode.EXCLUDED))
                .should(notExceedMaxParameters(maxParameters))
                .allowEmptyShould(true)
                .because("a long parameter list hides a missing abstraction");
    }

    private static ArchCondition<JavaCodeUnit> notExceedMaxParameters(int maxParameters) {
        return new ArchCondition<>("not declare more than %d parameters".formatted(maxParameters)) {
            @Override
            public void check(JavaCodeUnit codeUnit, ConditionEvents events) {
                int parameterCount = codeUnit.getRawParameterTypes().size();
                if (parameterCount > maxParameters) {
                    events.add(SimpleConditionEvent.violated(
                            codeUnit,
                            "%s declares %d parameters, at most %d are allowed"
                                    .formatted(codeUnit.getFullName(), parameterCount, maxParameters)));
                }
            }
        };
    }

    private String expectedContract() {
        return EXPECTED_CONTRACT.formatted(maxParameters);
    }
}
