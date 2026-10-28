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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NoJavaUtilDateRuleTest {

    private static final String ALLOWED_FIXTURE = "archfixtures.date.AllowedDate";

    private static List<String> violations() {
        return ArchRuleChecks.violations(NoJavaUtilDateRule.definition(), dateFixtures());
    }

    private static JavaClasses dateFixtures() {
        return new ClassFileImporter().importPackages("archfixtures.date");
    }

    @Test
    void dateFieldIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("LegacyDate.created", "java.util.Date"));
    }

    @Test
    void methodLevelDateUsageIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail).contains("MethodDate.now()", "java.util.Date"))
                .anySatisfy(detail -> assertThat(detail).contains("MethodDate.fromInstant()", "java.util.Date.from"));
    }

    @Test
    void localDateTimePasses() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("ModernDate"));
    }

    @Test
    void generatedQueryClassesAreNotChecked() {
        assertThat(violations()).noneSatisfy(detail -> assertThat(detail).contains("QDate"));
    }

    @Test
    void allowlistedClassIsNotReportedWhileSameUsageElsewhereIs() {
        List<String> allowlisted = ArchRuleChecks.violations(
                NoJavaUtilDateRule.definition(Map.of(ALLOWED_FIXTURE, "fixture boundary")), dateFixtures());

        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("AllowedDate.fromInstant()"));
        assertThat(allowlisted)
                .noneSatisfy(detail -> assertThat(detail).contains("AllowedDate"))
                .anySatisfy(detail -> assertThat(detail).contains("MethodDate.fromInstant()", "java.util.Date.from"));
    }

    @Test
    void noJavaUtilDateAllowlistIsConfigurable() {
        NoJavaUtilDateRule rule = new NoJavaUtilDateRule(Map.of(ALLOWED_FIXTURE, "reason")) {};

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.date")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("MethodDate.fromInstant()")
                .hasMessageNotContaining("AllowedDate");
    }

    @Test
    void defaultAllowlistIsEmpty() {
        assertThat(violations()).anySatisfy(detail -> assertThat(detail).contains("AllowedDate.fromInstant()"));
    }

    @Test
    void ruleInspectsMainAndTestClasses() {
        assertThat(new NoJavaUtilDateRule().scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
        assertThat(new NoJavaUtilDateRule().severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoJavaUtilDateRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
