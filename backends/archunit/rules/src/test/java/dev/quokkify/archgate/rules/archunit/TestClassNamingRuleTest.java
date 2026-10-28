package dev.quokkify.archgate.rules.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestClassNamingRuleTest {

    private static final String FIXTURE_PACKAGE = "archfixtures.naming";

    @Test
    void concreteSubclassOfTestBaseMustBeNamedTest() {
        assertThatThrownBy(() -> new TestClassNamingRule().verify(FixtureContexts.of(FIXTURE_PACKAGE)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("archfixtures.naming.InheritsTests")
                .hasMessageNotContaining("CorrectlyNamedTest")
                .hasMessageNotContaining("AbstractBase");
    }

    @Test
    @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
    void testClassNamingDetectsJUnit5Test() {
        assertThatThrownBy(() -> new TestClassNamingRule().verify(FixtureContexts.of(FIXTURE_PACKAGE)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("archfixtures.naming.Foo");
    }

    @Test
    void customAnnotationAndSuffixAreHonoured() {
        TestClassNamingRule rule = new TestClassNamingRule(Set.of("org.testng.annotations.Test"), "Tests") {};

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of(FIXTURE_PACKAGE)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("archfixtures.naming.CorrectlyNamedTest")
                .hasMessageContaining("*Tests")
                .hasMessageNotContaining("archfixtures.naming.Foo ");
    }

    @Test
    void testClassNamingNoTestClassesIsUnableToVerify() {
        TestClassNamingRule rule = new TestClassNamingRule();

        assertThatThrownBy(() -> rule.verify(new ClassFileImporter().importClasses()))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("test naming was not verified")
                .hasMessageNotContaining("casino");
    }

    @Test
    void emptyTestClassViewIsUnableToVerify() {
        assertThatThrownBy(() -> new TestClassNamingRule().verify(FixtureContexts.of("archfixtures.nosuchpackage")))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageNotContaining("casino");
    }

    @Test
    void ruleInspectsTestClassesOnly() {
        TestClassNamingRule rule = new TestClassNamingRule();

        assertThat(rule.scopes()).containsExactly(Scope.TEST);
        assertThat(rule.severity()).isEqualTo(Severity.ERROR);
        assertThat(rule.name()).isEqualTo("Test class naming");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new TestClassNamingRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
