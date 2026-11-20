package dev.quokkify.archgate.rules.archunit.tms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.tmslinkformat.MalformedIdsTest;
import archfixtures.tmslinkformat.PrefixedIdsTest;
import archfixtures.tmslinkformat.ValidIdsTest;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TmsLinkFormatRuleTest {

    private static final String FIXTURES = "archfixtures.tmslinkformat";
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);
    private static final String HINT = ": Cyrillic '\u0421' found, use the Latin 'C'"; // Cyrillic capital Es

    private static List<String> violations() {
        return TmsLinkFormatRule.violations(FIXTURE_CLASSES, TmsLinkFormatRule.DEFAULT_FORMAT, Map.of());
    }

    @DataProvider(name = "malformed")
    public Object[][] malformed() {
        return new Object[][] {
            {"zero", "0"},
            {"prefixedZero", "C0"},
            {"leadingZero", "007"},
            {"placeholder", "<case_id>"},
            {"letters", "abc"},
            {"innerSpace", "12 34"},
            {"outerSpace", " 123"},
            {"prefixed", "ABC-12-X"},
            {"lowercase", "c123"}
        };
    }

    @DataProvider(name = "cyrillic")
    public Object[][] cyrillic() {
        return new Object[][] {{"cyrillicUpper"}, {"cyrillicLower"}};
    }

    @Test(description = "Numeric ids with and without the Latin C prefix pass")
    public void validIdsPass() {
        assertThat(TmsLinkFormatRule.violations(
                        new ClassFileImporter().importClasses(ValidIdsTest.class),
                        TmsLinkFormatRule.DEFAULT_FORMAT,
                        Map.of()))
                .isEmpty();
    }

    @Test(description = "Malformed id is reported with class, method and value", dataProvider = "malformed")
    public void malformedIdIsReported(String method, String value) {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains(
                                "MalformedIdsTest.%s()".formatted(method),
                                "has a malformed @TmsLink \"%s\"".formatted(value))
                        .doesNotContain("Cyrillic"));
    }

    @Test(description = "Cyrillic C is reported with a hint to use the Latin C", dataProvider = "cyrillic")
    public void cyrillicPrefixGetsHint(String method) {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("UnicodeIdsTest.%s()".formatted(method), HINT));
    }

    @Test(description = "Cyrillic letter inside the value also gets the hint")
    public void cyrillicAnywhereGetsHint() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("UnicodeIdsTest.cyrillicInside()", HINT));
    }

    @Test(description = "Non-ASCII digits are reported")
    public void nonAsciiDigitsAreReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("UnicodeIdsTest.arabicDigits()")
                        .doesNotContain("Cyrillic"));
    }

    @Test(description = "Full-width Latin C is reported without the Cyrillic hint")
    public void fullWidthLatinLetterIsReportedWithoutHint() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("UnicodeIdsTest.fullWidthC()")
                        .doesNotContain("Cyrillic"));
    }

    @Test(description = "Each malformed value in TmsLinks is reported separately")
    public void containerValuesAreReportedOneByOne() {
        assertThat(violations())
                .filteredOn(detail -> detail.contains("ContainerTest.oneBad()"))
                .singleElement()
                .asString()
                .contains("\"x\"");
        assertThat(violations())
                .filteredOn(detail -> detail.contains("ContainerTest.twoBad()"))
                .hasSize(2)
                .anySatisfy(detail -> assertThat(detail).contains("\"y\""))
                .anySatisfy(detail -> assertThat(detail).contains("\"z\""));
    }

    @Test(description = "Blank values are left to TmsLinkRequiredRule")
    public void blankValuesAreNotReported() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("BlankIdsTest"));
    }

    @Test(description = "Configuration and helper methods are not checked")
    public void nonTestMethodsAreNotChecked() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("HelperMethods"));
    }

    @Test(description = "Class-level, disabled and inherited tests are checked")
    public void testRecognitionMatchesOtherRules() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("ClassLevelTest.classLevel()"));
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("DisabledTest.switchedOff()"));
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("AbstractBaseTest.inherited()"));
        assertThat(violations())
                .filteredOn(detail -> detail.contains("inherited()"))
                .hasSize(1);
    }

    @Test(description = "Rule inspects only test classes with warning severity and a stable name")
    public void ruleMetadata() {
        TmsLinkFormatRule rule = new TmsLinkFormatRule();

        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.TEST));
        assertThat(rule.severity()).isEqualTo(Severity.WARNING);
        assertThat(rule.name()).isEqualTo("TmsLink case id format");
    }

    @Test(description = "Excluded package, class annotation and method annotation are not checked")
    public void exclusionsAreNotChecked() {
        List<String> excluded = TmsLinkFormatRule.violations(
                FIXTURE_CLASSES, TmsLinkFormatRule.DEFAULT_FORMAT, ProjectTmsLinkFormatRule.EXCLUSIONS);

        assertThat(violations()).anySatisfy(d -> assertThat(d).contains("setup.ExcludedSetupTest.setup()"));
        assertThat(excluded)
                .noneSatisfy(d -> assertThat(d)
                        .containsAnyOf(
                                "ExcludedSetupTest",
                                "AnnotatedClassExcludedTest",
                                "AnnotatedMethodExcludedTest.setup()"));
        assertThat(excluded).anySatisfy(d -> assertThat(d).contains("AnnotatedMethodExcludedTest.real()"));
    }

    @Test(description = "Annotation key that no test class or method carries is rejected")
    public void misspelledAnnotationKeyIsRejected() {
        TmsLinkFormatRule rule = new TmsLinkFormatRule(Map.of("archfixtures.tmslinkformat.SetupOnlyp", "typo")) {};

        assertThatThrownBy(() -> rule.verify(fixtureContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("archfixtures.tmslinkformat.SetupOnlyp");
    }

    @Test(description = "Subclass pattern accepts prefixed ids")
    public void customPatternAcceptsPrefixedIds() {
        TmsLinkFormatRule custom = new TmsLinkFormatRule(Pattern.compile("C?[1-9]\\d*|[A-Z]+-\\d+-[A-Z]"), Map.of()) {};
        ArchUnitContext context =
                fixtureContext(new ClassFileImporter().importClasses(ValidIdsTest.class, PrefixedIdsTest.class));

        assertThatCode(() -> custom.verify(context)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new TmsLinkFormatRule().verify(context)).isInstanceOf(ArchitectureViolation.class);
    }

    @Test(description = "Custom pattern must match the whole value")
    public void customPatternMustMatchWholeValue() {
        assertThat(TmsLinkFormatRule.violations(
                        new ClassFileImporter().importClasses(MalformedIdsTest.class),
                        Pattern.compile("\\d+"),
                        Map.of()))
                .anySatisfy(d -> assertThat(d).contains("MalformedIdsTest.innerSpace()"));
    }

    @Test(description = "Custom pattern contract does not claim the default examples")
    public void customPatternContractOmitsDefaultExamples() {
        TmsLinkFormatRule custom = new TmsLinkFormatRule(Pattern.compile("\\d+"), Map.of()) {};

        assertThatThrownBy(() -> custom.verify(fixtureContext()))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("matching \\d+.")
                .hasMessageNotContaining("for example");
    }

    @Test(description = "Null pattern is rejected at construction")
    public void nullPatternIsRejected() {
        assertThatThrownBy(() -> new TmsLinkFormatRule(null, Map.of()) {}).isInstanceOf(NullPointerException.class);
    }

    @Test(description = "Rule verifies test classes and states the contract")
    public void verifyReportsWithContract() {
        assertThatThrownBy(() -> new ProjectTmsLinkFormatRule().verify(fixtureContext()))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("io.qameta.allure.TmsLink")
                .hasMessageContaining("C?[1-9]\\d*")
                .hasMessageContaining("MalformedIdsTest");
    }

    private static ArchUnitContext fixtureContext() {
        return fixtureContext(FIXTURE_CLASSES);
    }

    private static ArchUnitContext fixtureContext(JavaClasses testClasses) {
        return new FixtureContext(FIXTURES, testClasses);
    }
}
