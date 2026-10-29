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

class NoGenericThrowsRuleTest {

    private static List<String> violations() {
        JavaClasses throwsClause = new ClassFileImporter().importPackages("archfixtures.throwsclause");
        return ArchRuleChecks.violations(NoGenericThrowsRule.definition(), throwsClause);
    }

    @Test
    void throwsExceptionIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("GenericThrows.throwsException()", "Exception"));
    }

    @Test
    void throwsRuntimeExceptionIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("GenericThrows.throwsRuntimeException()", "RuntimeException"));
    }

    @Test
    void throwsThrowableIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("GenericThrows.throwsThrowable()", "Throwable"));
    }

    @Test
    void specificExceptionsPass() {
        assertThat(violations())
                .noneSatisfy(detail -> assertThat(detail).contains("SpecificThrows"))
                .noneSatisfy(detail -> assertThat(detail).contains("GenericThrows.specificChecked()"))
                .noneSatisfy(detail -> assertThat(detail).contains("GenericThrows.<init>"));
    }

    @Test
    void ruleInspectsMainClassesOnly() {
        assertThat(new NoGenericThrowsRule().scopes()).isEqualTo(Set.of(Scope.MAIN));
        assertThat(new NoGenericThrowsRule().severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void verifyReportsFixtureViolations() {
        NoGenericThrowsRule rule = new NoGenericThrowsRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.throwsclause")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("GenericThrows.throwsException()");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoGenericThrowsRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
