package dev.quokkify.archgate.rules.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

class NoDeprecatedApiUsageRuleTest {

    private static final String DEPRECATED = "archfixtures.deprecated";

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

    // regression guard for #43: a module without test classes was not checked at all
    @Test
    void mainOnlyModuleIsCheckedInMain() {
        NoDeprecatedApiUsageRule rule = new NoDeprecatedApiUsageRule();
        ArchUnitContext context =
                FixtureContexts.of(DEPRECATED, List.of(FixtureContexts.classesDir()), List.of(missingDir()));

        assertThatThrownBy(() -> rule.verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("UsesDeprecatedApi.readsField()");
        assertThat(rule.skippedScopes(context)).containsExactly(Scope.TEST);
    }

    @Test
    void testOnlyModuleIsCheckedInTest() {
        NoDeprecatedApiUsageRule rule = new NoDeprecatedApiUsageRule();
        ArchUnitContext context =
                FixtureContexts.of(DEPRECATED, List.of(missingDir()), List.of(FixtureContexts.classesDir()));

        assertThatThrownBy(() -> rule.verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("UsesDeprecatedApi.readsField()");
        assertThat(rule.skippedScopes(context)).containsExactly(Scope.MAIN);
    }

    @Test
    void bothViewsEmptyIsUnableToVerify() {
        NoDeprecatedApiUsageRule rule = new NoDeprecatedApiUsageRule();
        ArchUnitContext context = FixtureContexts.of(DEPRECATED, List.of(missingDir()), List.of(missingDir()));

        assertThatThrownBy(() -> rule.verify(context))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("(main and test) is empty");
    }

    @Test
    void bothViewsPresentSkipNothing() {
        NoDeprecatedApiUsageRule rule = new NoDeprecatedApiUsageRule();
        ArchUnitContext context = FixtureContexts.of(DEPRECATED);

        assertThatThrownBy(() -> rule.verify(context)).isInstanceOf(ArchitectureViolation.class);
        assertThat(rule.skippedScopes(context)).isEmpty();
    }

    @Test
    void narrowedScopesReadOnlyThatView() {
        NoDeprecatedApiUsageRule mainOnly = new NoDeprecatedApiUsageRule() {
            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.MAIN);
            }
        };
        ArchUnitContext context =
                FixtureContexts.of(DEPRECATED, List.of(FixtureContexts.classesDir()), List.of(missingDir()));

        assertThatThrownBy(() -> mainOnly.verify(context)).isInstanceOf(ArchitectureViolation.class);
        assertThat(mainOnly.skippedScopes(context)).isEmpty();
    }

    @Test
    void strictSubclassFailsOnEmptyTestView() {
        NoDeprecatedApiUsageRule strict = new NoDeprecatedApiUsageRule() {
            @Override
            public boolean requireAllScopes() {
                return true;
            }
        };
        ArchUnitContext context =
                FixtureContexts.of(DEPRECATED, List.of(FixtureContexts.classesDir()), List.of(missingDir()));

        assertThatThrownBy(() -> strict.verify(context))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("test class directories");
    }

    private static Path missingDir() {
        return FixtureContexts.classesDir().resolve("does-not-exist");
    }

    @Test
    void contextTypeIsArchUnit() {
        assertThat(new NoDeprecatedApiUsageRule().contextType()).isEqualTo(ArchUnitContext.class);
    }
}
