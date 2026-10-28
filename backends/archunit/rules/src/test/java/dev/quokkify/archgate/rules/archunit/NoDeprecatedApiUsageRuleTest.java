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

class NoDeprecatedApiUsageRuleTest {

    private static List<String> violations() {
        JavaClasses deprecated = new ClassFileImporter().importPackages("archfixtures.deprecated");
        return ArchRuleChecks.violations(NoDeprecatedApiUsageRule.definition(), deprecated);
    }

    @Test
    void deprecatedFieldAccessIsReported() {
        assertThat(violations())
                .anySatisfy(
                        detail -> assertThat(detail).contains("UsesDeprecatedApi.readsField()", "LegacyApi.OLD_FIELD"));
    }

    @Test
    void deprecatedMethodCallIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("UsesDeprecatedApi.callsMethod()", "LegacyApi.oldMethod()"));
    }

    @Test
    void callPassingDeprecatedParameterIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("UsesDeprecatedApi.passesDeprecatedParameter()", "withDeprecatedParameter"));
    }

    @Test
    void deprecatedConstructorCallIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("UsesDeprecatedApi.constructsDeprecated()", "LegacyApi.<init>()"));
    }

    @Test
    void extendingDeprecatedClassIsReported() {
        assertThat(violations())
                .anySatisfy(detail ->
                        assertThat(detail).contains("ExtendsDeprecatedClass", "extends deprecated", "DeprecatedBase"));
    }

    @Test
    void implementingDeprecatedInterfaceIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("ImplementsDeprecatedContract", "implements deprecated", "DeprecatedContract"));
    }

    @Test
    void callReturningDeprecatedTypeIsReported() {
        assertThat(violations())
                .anySatisfy(detail -> assertThat(detail)
                        .contains("UsesDeprecatedApi.assignsDeprecatedReturn()", "returningDeprecated"));
    }

    @Test
    void modernUsagePasses() {
        assertThat(violations())
                .noneSatisfy(detail -> assertThat(detail).contains("ModernUser"))
                .noneSatisfy(detail -> assertThat(detail).contains("ModernApi"));
    }

    @Test
    void ruleInspectsMainAndTestClasses() {
        assertThat(new NoDeprecatedApiUsageRule().scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
        assertThat(new NoDeprecatedApiUsageRule().severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void verifyReportsFixtureViolations() {
        NoDeprecatedApiUsageRule rule = new NoDeprecatedApiUsageRule();

        assertThatThrownBy(() -> rule.verify(FixtureContexts.of("archfixtures.deprecated")))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("UsesDeprecatedApi.readsField()");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoDeprecatedApiUsageRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
