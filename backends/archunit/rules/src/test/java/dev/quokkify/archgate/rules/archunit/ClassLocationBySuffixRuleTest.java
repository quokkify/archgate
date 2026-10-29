package dev.quokkify.archgate.rules.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

class ClassLocationBySuffixRuleTest {

    private static List<String> violations() {
        JavaClasses location = new ClassFileImporter().importPackages("archfixtures.location");
        return ArchRuleChecks.violations(new ClassLocationBySuffixRule().definition(), location);
    }

    @Test
    void stepsOutsideStepsPackageIsReported() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("MisplacedSteps", "steps"));
    }

    @Test
    void verificationOutsideVerificationsPackageIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("MisplacedVerification", "verifications"));
    }

    @Test
    void verificationsOutsideVerificationsPackageIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("MisplacedVerifications", "verifications"));
    }

    @Test
    void generatorOutsideGeneratorsPackageIsReported() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("MisplacedGenerator", "generators"));
    }

    @Test
    void correctlyPlacedPasses() {
        assertThat(violations())
                .noneSatisfy(detail -> assertThat(detail).contains("PlacedSteps"))
                .noneSatisfy(detail -> assertThat(detail).contains("PlacedVerification"))
                .noneSatisfy(detail -> assertThat(detail).contains("PlacedGenerator"));
    }

    @Test
    void enumsAreNotChecked() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("SomethingVerification"));
    }

    @Test
    void packageSegmentMustMatchExactly() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("SegmentPrefixSteps"))
                .anySatisfy(detail -> assertThat(detail).contains("SegmentSuffixSteps"));
    }

    @Test
    void nestedClassesAreNotChecked() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("InnerSteps"));
    }

    @Test
    void classLocationCustomMapping() {
        ClassLocationBySuffixRule rule = new ClassLocationBySuffixRule(Map.of("Page", "..pages..")) {};
        JavaClasses location = new ClassFileImporter().importPackages("archfixtures.location");

        assertThat(ArchRuleChecks.violations(rule.definition(), location))
                .anySatisfy(detail -> assertThat(detail).contains("archfixtures.location.misplaced.LoginPage"))
                .noneSatisfy(detail -> assertThat(detail).contains("MisplacedSteps"));
    }

    @Test
    void customMappingContractIsGeneratedFromTheMap() {
        ClassLocationBySuffixRule rule = new ClassLocationBySuffixRule(Map.of("Page", "..pages..")) {};

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.location")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("*Page")
                .hasMessageContaining("..pages..")
                .hasMessageContaining("LoginPage")
                .hasMessageNotContaining("*Steps");
    }

    @Test
    void defaultContractListsEveryMapping() {
        assertThatThrownBy(() -> new ClassLocationBySuffixRule().verify(FixtureContexts.of("archfixtures.location")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("*Steps")
                .hasMessageContaining("..steps..")
                .hasMessageContaining("*Verification")
                .hasMessageContaining("*Verifications")
                .hasMessageContaining("*Generator")
                .hasMessageContaining("..generators..");
    }

    @Test
    void emptyMappingIsRejected() {
        assertThatThrownBy(() -> new ClassLocationBySuffixRule(Map.of()) {})
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ruleInspectsMainClassesAsMandatoryContract() {
        ClassLocationBySuffixRule rule = new ClassLocationBySuffixRule();

        assertThat(rule.scopes()).isEqualTo(Set.of(Scope.MAIN));
        assertThat(rule.severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.name()).isEqualTo("Class location by suffix");
    }

    @Test
    void emptySelectionIsUnableToVerify() {
        JavaClasses none = new ClassFileImporter().importPackages("archfixtures.services");

        assertThatThrownBy(() -> new ClassLocationBySuffixRule().verify(none))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("was not verified at all");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new ClassLocationBySuffixRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
