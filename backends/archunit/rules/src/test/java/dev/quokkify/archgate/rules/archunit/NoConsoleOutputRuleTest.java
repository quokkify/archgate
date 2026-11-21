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

class NoConsoleOutputRuleTest {

    @Test
    void consoleOutputIsReported() {
        JavaClasses console = new ClassFileImporter().importPackages("archfixtures.console");

        List<String> violations = ArchRuleChecks.violations(NoConsoleOutputRule.definition(), console);

        assertThat(violations)
                .anySatisfy(detail -> assertThat(detail).contains("ConsoleWriter.printToStandardOut()", "System.out"))
                .anySatisfy(detail -> assertThat(detail).contains("ConsoleWriter.printToStandardErr()", "System.err"))
                .anySatisfy(detail -> assertThat(detail).contains("ConsoleWriter.printTrace(", "printStackTrace()"))
                .anySatisfy(detail -> assertThat(detail).contains("ConsoleWriter.dump()", "dumpStack()"))
                .noneSatisfy(detail -> assertThat(detail).contains("LoggingWriter"));
    }

    // Regression guard: javac encodes an unqualified dumpStack() inside a Thread subclass with the subclass as owner.
    @Test
    void unqualifiedDumpStackInThreadSubclassIsReported() {
        JavaClasses console = new ClassFileImporter().importPackages("archfixtures.console");

        assertThat(ArchRuleChecks.violations(NoConsoleOutputRule.definition(), console))
                .anySatisfy(detail -> assertThat(detail).contains("DumpingThread.dump()", "dumpStack()"));
    }

    // Regression guard: a method reference is not a method call in ArchUnit.
    @Test
    void consoleMethodReferencesAreReported() {
        JavaClasses console = new ClassFileImporter().importPackages("archfixtures.console");

        assertThat(ArchRuleChecks.violations(NoConsoleOutputRule.definition(), console))
                .anySatisfy(detail -> assertThat(detail).contains("ConsoleMethodReferences.dumpStack()", "dumpStack()"))
                .anySatisfy(detail ->
                        assertThat(detail).contains("ConsoleMethodReferences.printTrace()", "printStackTrace()"))
                .anySatisfy(detail -> assertThat(detail).contains("ConsoleMethodReferences.printLine()", "System.out"));
    }

    // Regression guard: overloads a Thread or Throwable subclass declares itself are not console output.
    @Test
    void ownOverloadsOfThreadAndThrowableSubclassesPass() {
        JavaClasses console = new ClassFileImporter().importPackages("archfixtures.console");

        assertThat(ArchRuleChecks.violations(NoConsoleOutputRule.definition(), console))
                .noneSatisfy(detail -> assertThat(detail).contains("OwnDumpThread"));
    }

    @Test
    void loggerAndExplicitStreamTraceAreAllowed() {
        JavaClasses logging = new ClassFileImporter().importClasses(archfixtures.console.LoggingWriter.class);

        assertThat(ArchRuleChecks.violations(NoConsoleOutputRule.definition(), logging))
                .isEmpty();
    }

    @Test
    void generatedQueryClassesAreNotChecked() {
        JavaClasses generated = new ClassFileImporter().importPackages("archfixtures.generated");

        List<String> violations = ArchRuleChecks.violations(NoConsoleOutputRule.definition(), generated);

        assertThat(violations)
                .anySatisfy(detail -> assertThat(detail).contains("PlainFoo"))
                .noneSatisfy(detail -> assertThat(detail).contains("QFoo"));
    }

    @Test
    void ruleInspectsMainClassesOnly() {
        assertThat(new NoConsoleOutputRule().scopes()).containsExactly(Scope.MAIN);
        assertThat(new NoConsoleOutputRule().severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void verifyReportsFixtureViolations() {
        NoConsoleOutputRule rule = new NoConsoleOutputRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.console")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("ConsoleWriter.printToStandardOut()");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoConsoleOutputRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
