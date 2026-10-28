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

class HashCodeEqualsTogetherRuleTest {

    private static List<String> violations() {
        JavaClasses hashCodeFixtures = new ClassFileImporter().importPackages("archfixtures.hashcode");
        return ArchRuleChecks.violations(HashCodeEqualsTogetherRule.definition(), hashCodeFixtures);
    }

    @Test
    void onlyEqualsIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("OnlyEquals", "overrides equals but not hashCode"));
    }

    @Test
    void onlyHashCodeIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("OnlyHashCode", "overrides hashCode but not equals"));
    }

    @Test
    void bothOverriddenPasses() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("BothOverridden"));
    }

    @Test
    void neitherOverriddenPasses() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("NeitherOverridden"));
    }

    @Test
    void ruleInspectsMainClassesOnly() {
        assertThat(new HashCodeEqualsTogetherRule().scopes()).isEqualTo(Set.of(Scope.MAIN));
        assertThat(new HashCodeEqualsTogetherRule().severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void verifyReportsFixtureViolations() {
        HashCodeEqualsTogetherRule rule = new HashCodeEqualsTogetherRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.hashcode")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("OnlyEquals");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new HashCodeEqualsTogetherRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
