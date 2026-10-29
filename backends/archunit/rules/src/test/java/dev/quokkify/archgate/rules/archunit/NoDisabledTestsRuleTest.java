package dev.quokkify.archgate.rules.archunit;

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
import java.util.Set;
import org.testng.annotations.Test;

class NoDisabledTestsRuleTest {

    private static List<String> violations() {
        JavaClasses disabled = new ClassFileImporter().importPackages("archfixtures.disabled");
        return ArchRuleChecks.violations(NoDisabledTestsRule.definition(), disabled);
    }

    @Test
    void testNgEnabledFalseIsReported() {
        assertThat(violations())
                .anySatisfy(
                        detail -> assertThat(detail).contains("DisabledByAttribute.doesNotRun()", "enabled = false"));
    }

    @Test
    void classLevelIgnoreIsReported() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("IgnoredTestClass"));
    }

    @Test
    void methodLevelIgnoreIsReported() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("IgnoredTestMethod.neverRuns()"));
    }

    @Test
    void classLevelEnabledFalseIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("DisabledByAttributeOnClass", "enabled = false"));
    }

    @Test
    void junitDisabledMethodIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("JupiterDisabledCase.neverRuns()", "Disabled"));
    }

    @Test
    void enabledTestsPass() {
        assertThat(violations())
                .noneSatisfy(detail -> assertThat(detail).contains("EnabledTestMethod"))
                .noneSatisfy(detail -> assertThat(detail).contains("JupiterEnabledCase"));
    }

    @Test
    void sameClassCanReportBothEnabledAndDisabledMethods() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("DisabledByAttribute.doesNotRun()"))
                .noneSatisfy(detail -> assertThat(detail).contains("DisabledByAttribute.runs()"));
    }

    @Test
    void ruleInspectsTestClassesOnly() {
        assertThat(new NoDisabledTestsRule().scopes()).isEqualTo(Set.of(Scope.TEST));
        assertThat(new NoDisabledTestsRule().severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void verifyReportsFixtureViolations() {
        NoDisabledTestsRule rule = new NoDisabledTestsRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.disabled")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("IgnoredTestMethod.neverRuns()");
    }

    @Test
    void annotationsAreConfigurable() {
        NoDisabledTestsRule testNgOnly = new NoDisabledTestsRule(
                Set.of("org.testng.annotations.Test"), Set.of("org.testng.annotations.Ignore")) {};

        assertThatThrownBy(() -> testNgOnly.verify(FixtureContexts.of("archfixtures.disabled")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("DisabledByAttribute.doesNotRun()");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoDisabledTestsRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
