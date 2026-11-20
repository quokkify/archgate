package dev.quokkify.archgate.rules.archunit.tms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.tmslink.AnnotatedClassExcludedTest;
import archfixtures.tmslink.AnnotatedMethodExcludedTest;
import archfixtures.tmslink.AnnotatedTest;
import archfixtures.tmslink.setup.ExcludedSetupTest;
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

public class TmsLinkRequiredRuleTest {

    private static final String FIXTURES = "archfixtures.tmslink";
    private static final Map<String, String> EXCLUDED_SETUP = ProjectTmsLinkRequiredRule.EXCLUSIONS;
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);

    private static List<String> violations() {
        return violations(Map.of());
    }

    private static List<String> violations(Map<String, String> excluded) {
        return ArchRuleChecks.violations(TmsLinkRequiredRule.definition(excluded), FIXTURE_CLASSES);
    }

    @Test(description = "Tests carrying a non-blank TmsLink or TmsLinks pass, helper methods are not tests")
    public void annotatedTestPasses() {
        assertThat(violations())
                .isNotEmpty()
                .noneSatisfy(detail ->
                        assertThat(detail).containsAnyOf("AnnotatedTest", "TmsLinksContainerTest", "HelperMethods"));
    }

    @Test(description = "Test method without TmsLink is reported")
    public void missingTmsLinkIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("MissingTmsLinkTest.noLink", "without @TmsLink"));
    }

    @Test(description = "TmsLinks on another method do not compensate for a test without TmsLink")
    public void annotationOnOtherMethodDoesNotCompensate() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("CompensatedCountTest.withoutLink"))
                .noneSatisfy(detail -> assertThat(detail).contains("CompensatedCountTest.withLinks"));
    }

    @Test(description = "Disabled test is still checked")
    public void disabledTestIsReported() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("DisabledTest.switchedOff"));
    }

    @Test(description = "Public methods of a class annotated with Test are checked, helpers and lambdas are not")
    public void classLevelTestMethodsAreChecked() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("ClassLevelTest.publicCase"))
                .noneSatisfy(detail -> assertThat(detail).containsAnyOf("ClassLevelTest.helper", "lambda$"));
    }

    @Test(
            description =
                    "Regression guard: configuration, factory, data provider and value-returning methods are skipped")
    public void classLevelTestSkipsNonTestMethods() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("ClassLevelConfigTest"));
    }

    @Test(description = "Regression guard: class-level Test on a superclass applies to subclass public methods")
    public void superclassLevelTestApplies() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("InheritedClassLevelTest.inheritedCase"));
    }

    @Test(description = "TmsLinks with only blank values is reported")
    public void allBlankTmsLinksIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("AllBlankTmsLinksTest.allBlank", "blank @TmsLink"));
    }

    @Test(description = "Blank TmsLink value is reported")
    public void blankTmsLinkIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("BlankTmsLinkTest.blankLink", "blank @TmsLink"));
    }

    @Test(description = "TmsLink on the class does not satisfy the test method")
    public void classLevelTmsLinkDoesNotCount() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("ClassLevelTmsLinkTest.classOnly"));
    }

    @Test(description = "Inherited test is reported on the declaring class only")
    public void inheritedTestIsReportedOnDeclaringClass() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("AbstractBaseTest.inherited"))
                .noneSatisfy(detail -> assertThat(detail).contains("ConcreteChildTest"));
    }

    @Test(description = "Nested classes are checked")
    public void nestedClassIsChecked() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("OuterTest$NestedTest.nested"));
    }

    @Test(description = "Excluded package is not checked while other packages still are")
    public void excludedPackageIsNotChecked() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("setup.ExcludedSetupTest.setup"));
        assertThat(violations(EXCLUDED_SETUP))
                .noneSatisfy(detail -> assertThat(detail).contains("ExcludedSetupTest"))
                .anySatisfy(detail -> assertThat(detail).contains("MissingTmsLinkTest"));
    }

    @Test(description = "Class carrying an excluded annotation is not checked")
    public void excludedByClassAnnotationIsNotChecked() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("AnnotatedClassExcludedTest.setup"));
        assertThat(violations(EXCLUDED_SETUP))
                .noneSatisfy(detail -> assertThat(detail).contains("AnnotatedClassExcludedTest"));
    }

    @Test(description = "Method carrying an excluded annotation is skipped, other tests of its class are still checked")
    public void excludedByMethodAnnotationSkipsOnlyThatMethod() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("AnnotatedMethodExcludedTest.setup"));
        assertThat(violations(EXCLUDED_SETUP))
                .noneSatisfy(detail -> assertThat(detail).contains("AnnotatedMethodExcludedTest.setup"))
                .anySatisfy(detail -> assertThat(detail).contains("AnnotatedMethodExcludedTest.real"));
    }

    @Test(description = "Excluded method of a class-level Test class is skipped, its sibling public method is checked")
    public void excludedByMethodAnnotationInClassLevelTest() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("ClassLevelExcludedMethodTest.setup"));
        assertThat(violations(EXCLUDED_SETUP))
                .noneSatisfy(detail -> assertThat(detail).contains("ClassLevelExcludedMethodTest.setup"))
                .anySatisfy(detail -> assertThat(detail).contains("ClassLevelExcludedMethodTest.real"));
    }

    @Test(description = "Key with '*' and without '..' is a package pattern")
    public void wildcardKeyIsPackagePattern() {
        assertThat(violations(Map.of("archfixtures.tmslink.set*", "test setup")))
                .noneSatisfy(detail -> assertThat(detail).contains("ExcludedSetupTest"))
                .anySatisfy(detail -> assertThat(detail).contains("MissingTmsLinkTest"));
    }

    @Test(description = "Test methods are checked whatever their visibility")
    public void nonPublicTestMethodsAreChecked() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("NonPublicTest.hidden"))
                .anySatisfy(detail -> assertThat(detail).contains("NonPublicTest.packagePrivate"));
    }

    @Test(description = "Blank exclusion key is rejected")
    public void blankExclusionKeyIsRejected() {
        TmsLinkRequiredRule rule = new TmsLinkRequiredRule(Map.of(" ", "no reason")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[ ]");
    }

    @Test(description = "Annotation key that no test class or method carries is rejected")
    public void misspelledAnnotationKeyIsRejected() {
        TmsLinkRequiredRule rule = new TmsLinkRequiredRule(Map.of("archfixtures.tmslink.SetupOnlyp", "typo")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archfixtures.tmslink.SetupOnlyp")
                .hasMessageContaining("'..'");
    }

    @Test(description = "Package name without '..' or '*' is not a package pattern and is rejected")
    public void plainPackageKeyIsRejected() {
        TmsLinkRequiredRule rule = new TmsLinkRequiredRule(Map.of("archfixtures.tmslink.setup", "exact package")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archfixtures.tmslink.setup");
    }

    @Test(description = "Valid exclusion keys are accepted")
    public void validExclusionKeysAreAccepted() {
        JavaClasses linkedOrExcluded = new ClassFileImporter()
                .importClasses(AnnotatedTest.class, AnnotatedClassExcludedTest.class, ExcludedSetupTest.class);

        assertThatCode(() -> new ProjectTmsLinkRequiredRule().verify(fixtureContext(linkedOrExcluded)))
                .doesNotThrowAnyException();
    }

    @Test(
            description =
                    "Exclusion annotation carried only by a method is accepted and the remaining violations still surface")
    public void methodOnlyExclusionKeyIsAcceptedAndOtherViolationsSurface() {
        JavaClasses methodExcluded = new ClassFileImporter().importClasses(AnnotatedMethodExcludedTest.class);
        TmsLinkRequiredRule rule = new TmsLinkRequiredRule(Map.of("archfixtures.tmslink.SetupOnly", "setup marker")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext(methodExcluded)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("AnnotatedMethodExcludedTest.real");
    }

    @Test(description = "Package pattern that matches no test class is rejected")
    public void unmatchedPackagePatternIsRejected() {
        TmsLinkRequiredRule rule = new TmsLinkRequiredRule(Map.of("..setpu..", "typo", "..setup..", "test setup")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[..setpu..]")
                .hasMessageContaining("match no test class");
    }

    @Test(description = "Rule description names only the exclusions in use, sorted by key")
    public void ruleDescriptionIsDeterministic() {
        assertThat(TmsLinkRequiredRule.definition(Map.of()).getDescription()).doesNotContain("excluded");
        assertThat(TmsLinkRequiredRule.definition(Map.of("..b..", "second", "..a..", "first"))
                        .getDescription())
                .contains("{..a..=first, ..b..=second}")
                .doesNotContain("annotation");
    }

    @Test(description = "Rule verifies test classes and states the contract")
    public void verifyReportsWithContract() {
        TmsLinkRequiredRule rule = new ProjectTmsLinkRequiredRule();

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("io.qameta.allure.TmsLink")
                .hasMessageContaining("MissingTmsLinkTest");
    }

    @Test(description = "Rule inspects only test classes with warning severity")
    public void ruleInspectsTestClassesOnly() {
        assertThat(new TmsLinkRequiredRule().scopes()).isEqualTo(Set.of(Scope.TEST));
        assertThat(new TmsLinkRequiredRule().severity()).isEqualTo(Severity.WARNING);
    }

    private static ArchUnitContext fixtureContext() {
        return fixtureContext(FIXTURE_CLASSES);
    }

    private static ArchUnitContext fixtureContext(JavaClasses testClasses) {
        return new FixtureContext(FIXTURES, testClasses);
    }
}
