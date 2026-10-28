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
import org.junit.jupiter.api.Test;

class TestAssertionsRuleTest {

    private static List<String> violations() {
        JavaClasses assertions = new ClassFileImporter().importPackages("archfixtures.assertions");
        return ArchRuleChecks.violations(TestAssertionsRule.definition(), assertions);
    }

    @Test
    void testNgAssertIsRecognized() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("AssertingTest.checksTotal()"));
    }

    @Test
    void testWithoutAssertionsIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("AssertingTest.callsHelperWithoutAsserting()", "does not assert"));
    }

    @Test
    void emptyTestIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("EmptyTest.runsCodeButChecksNothing()", "does not assert"));
    }

    @Test
    void assertThatApiIsRecognized() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("AssertJTest"));
    }

    @Test
    void softAssertIsRecognized() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("SoftAssertTest"));
    }

    @Test
    void ruleInspectsTestClassesOnly() {
        assertThat(new TestAssertionsRule().scopes()).isEqualTo(Set.of(Scope.TEST));
        assertThat(new TestAssertionsRule().severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void verifyReportsFixtureViolations() {
        TestAssertionsRule rule = new TestAssertionsRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.assertions")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("EmptyTest.runsCodeButChecksNothing()");
    }

    @Test
    void assertionOwnersAreConfigurable() {
        TestAssertionsRule testNgOnly =
                new TestAssertionsRule(Set.of("org.testng.annotations.Test"), Set.of("org.testng.Assert")) {};

        assertThatThrownBy(() -> testNgOnly.verify(FixtureContexts.of("archfixtures.assertions")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("AssertJTest.checksName()");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new TestAssertionsRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
