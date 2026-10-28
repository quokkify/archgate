package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
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

public class UtilityClassFinalityRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      A class with only static methods must be final and have a single private constructor. A utility \
      class without them can be extended or instantiated, which creates broken or stateful subclasses \
      of a class that was meant as a namespace for functions.""";

    @Override
    public String name() {
        return "Utility class finality";
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
        return ArchRuleDefinition.classes()
                .that(not(GeneratedCode.EXCLUDED))
                .should(beFinalUtilityWithPrivateConstructor())
                .allowEmptyShould(true)
                .because("utility classes are namespaces for functions, not types to extend");
    }

    private static ArchCondition<JavaClass> beFinalUtilityWithPrivateConstructor() {
        return new ArchCondition<>("be final with a private constructor when all methods are static") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (javaClass.isEnum()
                        || javaClass.isInterface()
                        || javaClass.getModifiers().contains(JavaModifier.ABSTRACT)
                        || !hasOnlyStaticMethods(javaClass)) {
                    return;
                }
                if (!javaClass.getModifiers().contains(JavaModifier.FINAL)) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            "%s has only static methods but is not final".formatted(javaClass.getDescription())));
                }
                if (javaClass.getConstructors().stream()
                        .noneMatch(constructor -> constructor.getModifiers().contains(JavaModifier.PRIVATE))) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            "%s has only static methods but no private constructor"
                                    .formatted(javaClass.getDescription())));
                }
            }
        };
    }

    private static boolean hasOnlyStaticMethods(JavaClass javaClass) {
        return !javaClass.getMethods().isEmpty()
                && javaClass.getMethods().stream()
                        .allMatch(method -> method.getModifiers().contains(JavaModifier.STATIC)
                                && !"main".equals(method.getName()));
    }
}
