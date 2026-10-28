package dev.quokkify.archgate.rules.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class TestClassNamingRule implements ArchitectureRule<ArchUnitContext> {

    private static final Set<String> DEFAULT_TEST_ANNOTATIONS =
            Set.of("org.testng.annotations.Test", "org.junit.jupiter.api.Test");
    private static final String DEFAULT_SUFFIX = "Test";

    private final Set<String> testAnnotations;
    private final String suffix;

    public TestClassNamingRule() {
        this(DEFAULT_TEST_ANNOTATIONS, DEFAULT_SUFFIX);
    }

    protected TestClassNamingRule(Set<String> testAnnotations, String suffix) {
        this.testAnnotations = Set.copyOf(testAnnotations);
        this.suffix = suffix;
    }

    @Override
    public String name() {
        return "Test class naming";
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
        verify(context.test());
    }

    void verify(JavaClasses testClasses) {
        if (testClasses.isEmpty()) {
            throw new UnableToVerifyError("""
          No test class was found, so test naming was not verified at all. Build the test classes first and \
          check the test class directories passed to the verification.""");
        }
        List<String> violations = testClasses.stream()
                .filter(TestClassNamingRule::isVerifiable)
                .filter(this::declaresTest)
                .filter(testClass -> !testClass.getSimpleName().endsWith(suffix))
                .map(testClass ->
                        "%s declares a test method but is not named *%s".formatted(testClass.getName(), suffix))
                .sorted()
                .toList();
        checkViolations(expectedContract(), violations);
    }

    private String expectedContract() {
        return ("Every class declaring test methods (annotated with %s) must have a name ending in '%s', otherwise "
                        + "suite generation does not pick it up and its tests never run. Rename the class, or move the shared "
                        + "code into a support class without test methods.")
                .formatted(String.join(" or ", sortedAnnotations()), suffix);
    }

    private List<String> sortedAnnotations() {
        return testAnnotations.stream().sorted().toList();
    }

    private static boolean isVerifiable(JavaClass testClass) {
        return !testClass.getModifiers().contains(JavaModifier.ABSTRACT)
                && !testClass.isInterface()
                && !testClass.isAnonymousClass()
                && !testClass.getModifiers().contains(JavaModifier.SYNTHETIC);
    }

    private boolean declaresTest(JavaClass testClass) {
        return Stream.concat(Stream.of(testClass), testClass.getAllRawSuperclasses().stream())
                .anyMatch(this::declaresOwnTest);
    }

    private boolean declaresOwnTest(JavaClass javaClass) {
        return testAnnotations.stream().anyMatch(javaClass::isAnnotatedWith)
                || javaClass.getMethods().stream()
                        .anyMatch(method -> testAnnotations.stream().anyMatch(method::isAnnotatedWith));
    }
}
