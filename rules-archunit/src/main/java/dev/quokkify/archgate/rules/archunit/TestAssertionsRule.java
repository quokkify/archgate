package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
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

public class TestAssertionsRule implements ArchitectureRule<ArchUnitContext> {

    private static final Set<String> DEFAULT_TEST_ANNOTATIONS =
            Set.of("org.testng.annotations.Test", "org.junit.jupiter.api.Test");
    private static final Set<String> DEFAULT_ASSERTION_OWNERS = Set.of(
            "org.testng.Assert",
            "org.testng.asserts.Assertion",
            "org.testng.asserts.SoftAssert",
            "org.assertj.core.api.Assertions",
            "org.junit.jupiter.api.Assertions");
    private static final String EXPECTED_CONTRACT = """
      Every test method must assert or verify something through one of the assertion APIs (for example \
      TestNG Assert, AssertJ, or Mockito.verify). A test without an assertion runs the code but checks \
      nothing, so it inflates the coverage numbers while guarding no behavior.""";

    private final Set<String> testAnnotations;
    private final Set<String> assertionOwners;

    public TestAssertionsRule() {
        this(DEFAULT_TEST_ANNOTATIONS, DEFAULT_ASSERTION_OWNERS);
    }

    protected TestAssertionsRule(Set<String> testAnnotations, Set<String> assertionOwners) {
        this.testAnnotations = Set.copyOf(testAnnotations);
        this.assertionOwners = Set.copyOf(assertionOwners);
    }

    @Override
    public String name() {
        return "Test assertions";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
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
                ArchRuleChecks.violations(definition(testAnnotations, assertionOwners), context.test());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return definition(DEFAULT_TEST_ANNOTATIONS, DEFAULT_ASSERTION_OWNERS);
    }

    static ArchRule definition(Set<String> testAnnotations, Set<String> assertionOwners) {
        return ArchRuleDefinition.classes()
                .that(not(GeneratedCode.EXCLUDED))
                .should(containAssertingTests(testAnnotations, assertionOwners))
                .allowEmptyShould(true)
                .because("a test without assertions is green even when the behavior is broken");
    }

    private static ArchCondition<JavaClass> containAssertingTests(
            Set<String> testAnnotations, Set<String> assertionOwners) {
        return new ArchCondition<>("contain tests that assert something") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getMethods().stream()
                        .filter(method -> testAnnotations.stream().anyMatch(method::isAnnotatedWith))
                        .filter(method -> method.getMethodCallsFromSelf().stream()
                                .noneMatch(call -> assertsSomething(call, assertionOwners)))
                        .forEach(method -> events.add(SimpleConditionEvent.violated(
                                method, "%s does not assert or verify anything".formatted(method.getDescription()))));
            }
        };
    }

    private static boolean assertsSomething(JavaMethodCall call, Set<String> assertionOwners) {
        return assertionOwners.contains(call.getTargetOwner().getName());
    }
}
