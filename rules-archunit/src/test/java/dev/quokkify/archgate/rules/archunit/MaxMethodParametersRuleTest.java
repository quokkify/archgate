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

class MaxMethodParametersRuleTest {

    private static List<String> violations() {
        JavaClasses parameters = new ClassFileImporter().importPackages("archfixtures.parameters");
        return ArchRuleChecks.violations(MaxMethodParametersRule.definition(), parameters);
    }

    @Test
    void sixParameterMethodIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("WideSignature.takesSix", "declares 6 parameters", "at most 5"));
    }

    @Test
    void sixParameterConstructorIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("WideSignature.<init>", "declares 6 parameters"));
    }

    @Test
    void fiveParameterUnitsPass() {
        assertThat(violations())
                .noneSatisfy(detail -> assertThat(detail).contains("takesFive"))
                .noneSatisfy(detail -> assertThat(detail).contains("NarrowSignature"));
    }

    @Test
    void ruleInspectsMainAndTestClasses() {
        assertThat(new MaxMethodParametersRule().scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
        assertThat(new MaxMethodParametersRule().severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void limitIsConfigurable() {
        MaxMethodParametersRule limitOfTwo = new MaxMethodParametersRule(2) {};
        JavaClasses parameters = new ClassFileImporter().importPackages("archfixtures.parameters");

        List<String> narrowViolations = ArchRuleChecks.violations(MaxMethodParametersRule.definition(2), parameters);
        assertThat(narrowViolations)
                .anySatisfy(detail -> assertThat(detail).contains("takesFive", "at most 2"))
                .anySatisfy(detail -> assertThat(detail).contains("takesSix", "at most 2"))
                .noneSatisfy(detail -> assertThat(detail).contains("NarrowSignature.takesTwo"));
        assertThatThrownBy(() -> limitOfTwo.verify(FixtureContexts.of("archfixtures.parameters")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("at most 2 are allowed");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new MaxMethodParametersRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
