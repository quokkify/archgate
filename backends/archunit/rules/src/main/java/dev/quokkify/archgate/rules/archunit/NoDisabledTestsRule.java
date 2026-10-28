package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.core.domain.JavaAnnotation;
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

public class NoDisabledTestsRule implements ArchitectureRule<ArchUnitContext> {

    private static final String ENABLED_ATTRIBUTE = "enabled";
    private static final Set<String> DEFAULT_TEST_ANNOTATIONS =
            Set.of("org.testng.annotations.Test", "org.junit.jupiter.api.Test");
    private static final Set<String> DEFAULT_DISABLING_ANNOTATIONS =
            Set.of("org.testng.annotations.Ignore", "org.junit.jupiter.api.Disabled");
    private static final String EXPECTED_CONTRACT = """
      Tests must not be disabled: neither %s on a class or method, nor %s with enabled = false. A disabled \
      test guards nothing while it still looks like coverage — remove it if it is obsolete, or fix and \
      re-enable it.""".formatted(
                    String.join(" or ", sorted(DEFAULT_DISABLING_ANNOTATIONS)),
                    String.join(" or ", sorted(DEFAULT_TEST_ANNOTATIONS)));

    private final Set<String> testAnnotations;
    private final Set<String> disablingAnnotations;

    public NoDisabledTestsRule() {
        this(DEFAULT_TEST_ANNOTATIONS, DEFAULT_DISABLING_ANNOTATIONS);
    }

    protected NoDisabledTestsRule(Set<String> testAnnotations, Set<String> disablingAnnotations) {
        this.testAnnotations = Set.copyOf(testAnnotations);
        this.disablingAnnotations = Set.copyOf(disablingAnnotations);
    }

    @Override
    public String name() {
        return "No disabled tests";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.TEST);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        List<String> violations =
                ArchRuleChecks.violations(definition(testAnnotations, disablingAnnotations), context.test());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return definition(DEFAULT_TEST_ANNOTATIONS, DEFAULT_DISABLING_ANNOTATIONS);
    }

    static ArchRule definition(Set<String> testAnnotations, Set<String> disablingAnnotations) {
        return ArchRuleDefinition.classes()
                .that(not(GeneratedCode.EXCLUDED))
                .should(notContainDisabledTests(testAnnotations, disablingAnnotations))
                .allowEmptyShould(true)
                .because("a disabled test is dead weight that pretends to guard the behavior it checks");
    }

    private static ArchCondition<JavaClass> notContainDisabledTests(
            Set<String> testAnnotations, Set<String> disablingAnnotations) {
        return new ArchCondition<>("not contain disabled tests") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                disablingAnnotations.stream()
                        .filter(javaClass::isAnnotatedWith)
                        .forEach(annotation -> events.add(SimpleConditionEvent.violated(
                                javaClass,
                                "%s disables the tests of %s".formatted(annotation, javaClass.getDescription()))));
                testAnnotations.stream()
                        .map(javaClass::tryGetAnnotationOfType)
                        .flatMap(java.util.Optional::stream)
                        .filter(NoDisabledTestsRule::isExplicitlyDisabled)
                        .forEach(test -> events.add(SimpleConditionEvent.violated(
                                javaClass,
                                "%s is annotated with %s with enabled = false"
                                        .formatted(
                                                javaClass.getDescription(),
                                                test.getRawType().getName()))));
                javaClass.getMembers().forEach(member -> {
                    disablingAnnotations.stream()
                            .filter(member::isAnnotatedWith)
                            .forEach(annotation -> events.add(SimpleConditionEvent.violated(
                                    member, "%s is annotated with %s".formatted(member.getDescription(), annotation))));
                    testAnnotations.stream()
                            .map(member::tryGetAnnotationOfType)
                            .flatMap(java.util.Optional::stream)
                            .filter(NoDisabledTestsRule::isExplicitlyDisabled)
                            .forEach(test -> events.add(SimpleConditionEvent.violated(
                                    member,
                                    "%s declares %s with enabled = false"
                                            .formatted(
                                                    member.getDescription(),
                                                    test.getRawType().getName()))));
                });
            }
        };
    }

    private static boolean isExplicitlyDisabled(JavaAnnotation<?> annotation) {
        return annotation
                .tryGetExplicitlyDeclaredProperty(ENABLED_ATTRIBUTE)
                .filter(Boolean.FALSE::equals)
                .isPresent();
    }

    private static List<String> sorted(Set<String> annotations) {
        return annotations.stream().sorted().toList();
    }
}
