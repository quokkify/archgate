package dev.quokkify.archgate.rules.archunit.tms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.uniquetmslink.AbstractBaseTest;
import archfixtures.uniquetmslink.BridgedOverrideTest;
import archfixtures.uniquetmslink.ConcreteChildTest;
import archfixtures.uniquetmslink.GenericCheck;
import archfixtures.uniquetmslink.HelperMethods;
import archfixtures.uniquetmslink.MultipleIdsTest;
import archfixtures.uniquetmslink.SameClassDuplicateTest;
import archfixtures.uniquetmslink.UniqueIdsTest;
import archfixtures.uniquetmslink.WhitespaceAndBlankIdsTest;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class TmsLinkUniqueRuleTest {

    private static final String FIXTURES = "archfixtures.uniquetmslink";
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);
    private static final Map<String, String> EXCLUDED_SETUP = ProjectTmsLinkUniqueRule.EXCLUSIONS;

    private static List<String> violations() {
        return TmsLinkUniqueRule.violations(FIXTURE_CLASSES, Map.of(), false);
    }

    private static List<String> violationsAllowingSameClass() {
        return TmsLinkUniqueRule.violations(FIXTURE_CLASSES, Map.of(), true);
    }

    @Test(description = "Unique ids, several ids on one method and non-test methods pass")
    public void uniqueIdsPass() {
        JavaClasses unique = new ClassFileImporter()
                .importClasses(
                        UniqueIdsTest.class,
                        MultipleIdsTest.class,
                        HelperMethods.class,
                        AbstractBaseTest.class,
                        ConcreteChildTest.class);

        assertThat(TmsLinkUniqueRule.violations(unique, Map.of(), false)).isEmpty();
    }

    @Test(description = "Id from a TmsLinks container next to a TmsLink is compared with other methods")
    public void containerIdNextToTmsLinkIsCompared() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains(
                                "TmsLink id <C24>",
                                "MultipleIdsTest.linkAndContainer()",
                                "ContainerDuplicateTest.sameIdAsContainer()"));
    }

    @Test(description = "Padded id is stripped and collides with its canonical form, blank ids are ignored")
    public void whitespaceIsStrippedAndBlankIdsAreIgnored() {
        JavaClasses ids = new ClassFileImporter().importClasses(WhitespaceAndBlankIdsTest.class);

        assertThat(TmsLinkUniqueRule.violations(ids, Map.of(), false))
                .singleElement()
                .satisfies(detail -> assertThat(detail)
                        .contains(
                                "TmsLink id <C110>",
                                "2 test methods",
                                "WhitespaceAndBlankIdsTest.padded()",
                                "WhitespaceAndBlankIdsTest.canonical()"));
    }

    @Test(description = "Regression guard: a compiler-generated bridge does not count as a second test method")
    public void bridgeMethodDoesNotDuplicateItsOverride() {
        JavaClasses bridged = new ClassFileImporter().importClasses(BridgedOverrideTest.class, GenericCheck.class);

        assertThat(TmsLinkUniqueRule.violations(bridged, Map.of(), false)).isEmpty();
    }

    @Test(description = "Id shared by test methods of different classes is reported with every method")
    public void crossClassDuplicateIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains(
                                "TmsLink id <C40>",
                                "2 test methods",
                                "CrossClassFirstTest.first()",
                                "CrossClassSecondTest.second()"));
    }

    @Test(description = "Each shared id is reported once")
    public void sharedIdIsReportedOnce() {
        assertThat(violations()).filteredOn(detail -> detail.contains("<C40>")).hasSize(1);
    }

    @Test(description = "Violations are ordered by id, shorter ids first")
    public void violationsAreOrderedById() {
        assertThat(violations())
                .map(detail -> detail.substring(detail.indexOf('<') + 1, detail.indexOf('>')))
                .containsSubsequence("C9", "C10", "C11", "C40", "C100");
    }

    @Test(description = "Id shared within one class is reported by default")
    public void sameClassDuplicateIsReportedByDefault() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains(
                                "TmsLink id <C50>",
                                "SameClassDuplicateTest.invalidScope()",
                                "SameClassDuplicateTest.invalidOrder()"));
    }

    @Test(description = "Public methods of a class annotated with Test are compared")
    public void classLevelTestMethodsAreCompared() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("TmsLink id <C80>", "ClassLevelTest.firstCase()", "ClassLevelTest.secondCase()"));
    }

    @Test(description = "Disabled test is still compared")
    public void disabledTestIsCompared() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("TmsLink id <C70>", "DisabledDuplicateTest.switchedOff()"));
    }

    @Test(description = "Id shared within one class passes when same-class duplicates are allowed")
    public void sameClassDuplicatePassesWhenAllowed() {
        assertThat(violationsAllowingSameClass())
                .noneSatisfy(detail -> assertThat(detail).containsAnyOf("<C50>", "<C70>", "<C80>"))
                .anySatisfy(detail -> assertThat(detail).contains("<C40>"));
    }

    @Test(description = "Id shared within one class and with another class is reported even when allowed")
    public void mixedDuplicateIsReportedWhenAllowed() {
        assertThat(violationsAllowingSameClass())
                .anySatisfy(detail -> assertThat(detail)
                        .contains(
                                "TmsLink id <C60>",
                                "3 test methods",
                                "MixedDuplicateTest.one()",
                                "MixedDuplicateTest.two()",
                                "CrossClassSecondTest.alsoMixed()"));
    }

    @Test(description = "Excluded package does not take part in the comparison")
    public void excludedPackageIsNotCompared() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("setup.ExcludedSetupTest.setup()"));
        assertThat(TmsLinkUniqueRule.violations(FIXTURE_CLASSES, EXCLUDED_SETUP, false))
                .noneSatisfy(detail -> assertThat(detail).contains("<C20>"));
    }

    @Test(description = "Class carrying an excluded annotation does not take part in the comparison")
    public void excludedByClassAnnotationIsNotCompared() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("AnnotatedClassExcludedTest.setup()"));
        assertThat(TmsLinkUniqueRule.violations(FIXTURE_CLASSES, EXCLUDED_SETUP, false))
                .noneSatisfy(detail -> assertThat(detail).contains("<C10>"));
    }

    @Test(description = "Method carrying an excluded annotation is skipped, other methods sharing its id are reported")
    public void excludedByMethodAnnotationSkipsOnlyThatMethod() {
        assertThat(TmsLinkUniqueRule.violations(FIXTURE_CLASSES, EXCLUDED_SETUP, false))
                .anySatisfy(detail -> assertThat(detail)
                        .contains("TmsLink id <C11>", "UniqueIdsTest.second()", "AnnotatedMethodExcludedTest.real()")
                        .doesNotContain("AnnotatedMethodExcludedTest.setup()"));
    }

    @Test(description = "Annotation key that no test class or method carries is rejected")
    public void misspelledAnnotationKeyIsRejected() {
        TmsLinkUniqueRule rule = new TmsLinkUniqueRule(Map.of("archfixtures.uniquetmslink.SetupOnlyp", "typo")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archfixtures.uniquetmslink.SetupOnlyp");
    }

    @Test(description = "Rule verifies test classes and states the contract")
    public void verifyReportsWithContract() {
        assertThatThrownBy(() -> new ProjectTmsLinkUniqueRule().verify(fixtureContext()))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("io.qameta.allure.TmsLink")
                .hasMessageContaining("CrossClassFirstTest");
    }

    @Test(description = "Subclass allowing same-class duplicates passes on a class with an internal duplicate only")
    public void subclassAllowsSameClassDuplicates() {
        TmsLinkUniqueRule rule = new TmsLinkUniqueRule(Map.of()) {
            @Override
            protected boolean allowDuplicatesWithinClass() {
                return true;
            }
        };
        JavaClasses sameClass = new ClassFileImporter().importClasses(SameClassDuplicateTest.class);

        assertThatCode(() -> rule.verify(fixtureContext(sameClass))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new TmsLinkUniqueRule().verify(fixtureContext(sameClass)))
                .isInstanceOf(ArchitectureViolation.class);
    }

    @Test(description = "Rule inspects only test classes with warning severity")
    public void ruleInspectsTestClassesOnly() {
        assertThat(new TmsLinkUniqueRule().scopes()).isEqualTo(Set.of(Scope.TEST));
        assertThat(new TmsLinkUniqueRule().severity()).isEqualTo(Severity.WARNING);
    }

    private static ArchUnitContext fixtureContext() {
        return fixtureContext(FIXTURE_CLASSES);
    }

    private static ArchUnitContext fixtureContext(JavaClasses testClasses) {
        return new FixtureContext(FIXTURES, testClasses);
    }
}
