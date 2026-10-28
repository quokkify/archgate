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

class UtilityClassFinalityRuleTest {

    private static List<String> violations() {
        JavaClasses utility = new ClassFileImporter().importPackages("archfixtures.utility");
        return ArchRuleChecks.violations(UtilityClassFinalityRule.definition(), utility);
    }

    @Test
    void classesWhoseOnlyStaticWorkIsMainPass() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("Launcher"));
    }

    @Test
    void enumsPass() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("Status"));
    }

    @Test
    void finalUtilityWithPrivateConstructorPasses() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("GoodUtility"));
    }

    @Test
    void nonFinalUtilityWithoutPrivateConstructorIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("OpenUtility", "is not final"))
                .anySatisfy(detail -> assertThat(detail).contains("OpenUtility", "no private constructor"));
    }

    @Test
    void classesWithInstanceMethodsPass() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("StatelessService"));
    }

    @Test
    void interfacesPass() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("UtilityContract"));
    }

    @Test
    void ruleInspectsMainClassesOnly() {
        assertThat(new UtilityClassFinalityRule().scopes()).isEqualTo(Set.of(Scope.MAIN));
        assertThat(new UtilityClassFinalityRule().severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void verifyReportsFixtureViolations() {
        UtilityClassFinalityRule rule = new UtilityClassFinalityRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.utility")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("OpenUtility");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new UtilityClassFinalityRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
