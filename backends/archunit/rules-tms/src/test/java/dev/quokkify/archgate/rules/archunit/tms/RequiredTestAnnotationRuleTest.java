package dev.quokkify.archgate.rules.archunit.tms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class RequiredTestAnnotationRuleTest {

    private static final String FIXTURES = "archfixtures.requiredannotation";
    private static final String PREFIX = FIXTURES + ".";
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);
    private static final RequiredAnnotation MARKER = RequiredAnnotation.of(PREFIX + "RequiredMarker");

    private static List<String> violations(RequiredAnnotation annotation) {
        return violations(annotation, Map.of());
    }

    private static List<String> violations(RequiredAnnotation annotation, Map<String, String> exclusions) {
        return ArchRuleChecks.violations(
                RequiredTestAnnotationRule.definition(annotation, exclusions), FIXTURE_CLASSES);
    }

    @Test(description = "Test carrying the annotation passes")
    public void markedTestPasses() {
        assertThat(violations(MARKER))
                .isNotEmpty()
                .noneSatisfy(detail -> assertThat(detail).contains(PREFIX + "MarkedTest."));
    }

    @Test(description = "Test method without the annotation is reported")
    public void missingAnnotationIsReported() {
        assertThat(violations(MARKER))
                .anySatisfy(
                        detail -> assertThat(detail).contains("MissingMarkerTest.noMarker", "without @RequiredMarker"));
    }

    @Test(description = "Annotation on another method does not compensate")
    public void annotationOnOtherMethodDoesNotCompensate() {
        assertThat(violations(MARKER))
                .anySatisfy(detail -> assertThat(detail).contains("CompensatedTest.withoutMarker"))
                .noneSatisfy(detail -> assertThat(detail).contains("CompensatedTest.withMarker"));
    }

    @Test(description = "Annotation on the class does not compensate")
    public void classAnnotationDoesNotCompensate() {
        assertThat(violations(MARKER)).anySatisfy(detail -> assertThat(detail).contains("ClassMarkedTest.classOnly"));
    }

    @Test(description = "Non-test methods are not checked")
    public void nonTestMethodsAreNotChecked() {
        assertThat(violations(MARKER))
                .noneSatisfy(detail -> assertThat(detail)
                        .containsAnyOf(
                                "MarkedTest.helper",
                                "ClassLevelMarkerTest.setUp",
                                "ClassLevelMarkerTest.data",
                                "ClassLevelMarkerTest.helper",
                                "ClassLevelMarkerTest.marked"))
                .anySatisfy(detail -> assertThat(detail).contains("ClassLevelMarkerTest.unmarked"));
    }

    @Test(description = "Blank value passes unless the option is set")
    public void blankValuePassesWithoutOption() {
        assertThat(violations(MARKER))
                .noneSatisfy(detail -> assertThat(detail).contains("BlankMarkerTest"))
                .anySatisfy(detail -> assertThat(detail).contains("MissingMarkerTest"));
    }

    @Test(description = "Blank value is reported with the option")
    public void blankValueIsReportedWithOption() {
        assertThat(violations(MARKER.requiringNonBlankValue()))
                .anySatisfy(detail -> assertThat(detail).contains("BlankMarkerTest.blank", "blank @RequiredMarker"));
    }

    @Test(description = "Container counts when configured")
    public void containerCountsWhenConfigured() {
        assertThat(violations(MARKER.withContainer(PREFIX + "RequiredMarkers")))
                .noneSatisfy(detail -> assertThat(detail).contains("ContainerMarkerTest"))
                .anySatisfy(detail -> assertThat(detail).contains("MissingMarkerTest"));
    }

    @Test(description = "Repeated annotation without a configured container counts as missing")
    public void repeatedWithoutContainerIsMissing() {
        assertThat(violations(MARKER))
                .anySatisfy(detail ->
                        assertThat(detail).contains("ContainerMarkerTest.repeated", "without @RequiredMarker"));
    }

    @Test(description = "Annotation name that no test carries reports every test")
    public void unknownAnnotationReportsEveryTest() {
        assertThat(violations(RequiredAnnotation.of(PREFIX + "Missing")))
                .anySatisfy(detail -> assertThat(detail).contains(PREFIX + "MarkedTest.marked"))
                .anySatisfy(detail -> assertThat(detail).contains("CompensatedTest.withMarker"));
    }

    @Test(description = "Inherited test is reported on the declaring class only")
    public void inheritedTestIsReportedOnDeclaringClass() {
        assertThat(violations(MARKER))
                .anySatisfy(detail -> assertThat(detail).contains("AbstractMarkerBaseTest.inherited"))
                .noneSatisfy(detail -> assertThat(detail).contains("ConcreteMarkerChildTest"));
    }

    @Test(description = "Nested classes are checked")
    public void nestedClassIsChecked() {
        assertThat(violations(MARKER))
                .anySatisfy(detail -> assertThat(detail).contains("OuterMarkerTest$NestedTest.nested"));
    }

    @Test(description = "Non-String value counts as blank only with the option")
    public void nonStringValueIsBlank() {
        RequiredAnnotation numeric = RequiredAnnotation.of(PREFIX + "NumericMarker");

        assertThat(violations(numeric))
                .noneSatisfy(detail -> assertThat(detail).contains("NumericMarkerTest"))
                .anySatisfy(detail -> assertThat(detail).contains("MissingMarkerTest"));
        assertThat(violations(numeric.requiringNonBlankValue()))
                .anySatisfy(detail -> assertThat(detail).contains("NumericMarkerTest.numeric", "blank @NumericMarker"));
    }

    @Test(description = "Excluded package and excluded annotation are skipped")
    public void exclusionsSkipPackageAndAnnotation() {
        assertThat(violations(MARKER, ProjectRequiredMarkerRule.EXCLUSIONS))
                .noneSatisfy(
                        detail -> assertThat(detail).containsAnyOf("ExcludedSetupTest", "AnnotationExcludedTest.setup"))
                .anySatisfy(detail -> assertThat(detail).contains("AnnotationExcludedTest.real"))
                .anySatisfy(detail -> assertThat(detail).contains("MissingMarkerTest"));
        assertThat(violations(MARKER, Map.of()))
                .anySatisfy(detail -> assertThat(detail).contains("ExcludedSetupTest.setup"))
                .anySatisfy(detail -> assertThat(detail).contains("AnnotationExcludedTest.setup"));
    }

    @Test(description = "Annotation exclusion key that nothing carries is rejected")
    public void unknownExclusionKeyIsRejected() {
        RequiredTestAnnotationRule rule =
                new RequiredTestAnnotationRule(MARKER, Map.of(PREFIX + "MarkerSetupp", "typo")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PREFIX + "MarkerSetupp");
    }

    @Test(description = "Name, severity and scope")
    public void defaults() {
        RequiredTestAnnotationRule rule = new ProjectRequiredMarkerRule();

        assertThat(rule.name()).isEqualTo("RequiredMarker on test methods");
        assertThat(rule.severity()).isEqualTo(Severity.WARNING);
        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.TEST));
    }

    @Test(description = "Rule runs against the ArchUnit context")
    public void contextTypeIsArchUnit() {
        assertThat(new ProjectRequiredMarkerRule().contextType()).isEqualTo(ArchUnitContext.class);
    }

    @Test(description = "Violations are reported together with the contract")
    public void verifyReportsViolationsWithContract() {
        assertThatThrownBy(() -> new ProjectRequiredMarkerRule().verify(fixtureContext()))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("needs @" + PREFIX + "RequiredMarker with a non-blank value")
                .hasMessageContaining("MissingMarkerTest");
    }

    private static ArchUnitContext fixtureContext() {
        return fixtureContext(FIXTURE_CLASSES);
    }

    private static ArchUnitContext fixtureContext(JavaClasses testClasses) {
        return new FixtureContext(FIXTURES, testClasses);
    }
}
