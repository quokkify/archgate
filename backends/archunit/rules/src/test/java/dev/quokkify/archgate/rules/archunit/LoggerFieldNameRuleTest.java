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
import org.testng.annotations.Test;

class LoggerFieldNameRuleTest {

    private static List<String> violations() {
        JavaClasses logger = new ClassFileImporter().importPackages("archfixtures.logger");
        return ArchRuleChecks.violations(LoggerFieldNameRule.definition(), logger);
    }

    @Test
    void loggerNamedLogIsReported() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("LowerCaseLogger.log", "name 'LOG'"));
    }

    @Test
    void slf4jLoggerNamedLogIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("Slf4jLowerCaseLogger.log", "name 'LOG'"));
    }

    @Test
    void nonStaticLoggerIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("InstanceLogger.LOG", "modifier STATIC"));
    }

    @Test
    void staticNonFinalLoggerIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("StaticNonFinalLogger.LOG", "modifier FINAL"));
    }

    @Test
    void loggerNamedUppercaseLogPasses() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("GoodLogger"));
    }

    @Test
    void nonLoggerFieldNamedLogIsNotReported() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("PlainStringField"));
    }

    @Test
    void generatedQueryClassesAreNotChecked() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("QLogger"));
    }

    @Test
    void ruleInspectsMainClassesOnly() {
        assertThat(new LoggerFieldNameRule().scopes()).containsExactly(Scope.MAIN);
        assertThat(new LoggerFieldNameRule().severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void verifyReportsFixtureViolations() {
        LoggerFieldNameRule rule = new LoggerFieldNameRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.logger")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("LowerCaseLogger.log");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new LoggerFieldNameRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
