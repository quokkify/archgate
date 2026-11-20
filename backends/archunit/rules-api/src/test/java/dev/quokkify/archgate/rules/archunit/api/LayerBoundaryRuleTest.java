package dev.quokkify.archgate.rules.archunit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;

public class LayerBoundaryRuleTest {

    private static final String FIXTURES = "archfixtures.layers";
    private static final String INVALID_ROOT = FIXTURES + ".invalid";
    private static final String VALID_ROOT = FIXTURES + ".valid";
    private static final String NO_LAYER_ROOT = FIXTURES + ".nolayer";
    private static final String LAYER = "domain";
    private static final Set<String> FORBIDDEN = Set.of("persistence", "web");
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(FIXTURES);

    private static List<String> violations(String rootPackage) {
        return ArchRuleChecks.violations(LayerBoundaryRule.definition(rootPackage, LAYER, FORBIDDEN), FIXTURE_CLASSES);
    }

    private static ArchUnitContext testOnly(String rootPackage) {
        return FixtureContexts.of(rootPackage, List.of(), List.of(FixtureContexts.classesDir()));
    }

    @Test(description = "A class in the layer that depends on a forbidden package is reported")
    public void layerDependingOnForbiddenPackageIsReported() {
        assertThat(violations(INVALID_ROOT))
                .anySatisfy(detail -> assertThat(detail).contains("OrderPricing.price", "OrderController"));
    }

    @Test(description = "A field whose type lives in a forbidden package is reported")
    public void fieldOfForbiddenTypeIsReported() {
        assertThat(violations(INVALID_ROOT))
                .anySatisfy(
                        detail -> assertThat(detail).contains("Field", "OrderArchive.repository", "OrderRepository"));
    }

    @Test(description = "Upper layers depending on the layer, and the layer on itself, pass")
    public void correctLayeringPasses() {
        assertThat(violations(VALID_ROOT)).isEmpty();
    }

    @Test(description = "Only the packages under the given root package are checked")
    public void otherRootsAreNotChecked() {
        assertThat(violations(INVALID_ROOT))
                .isNotEmpty()
                .noneSatisfy(detail -> assertThat(detail).contains(VALID_ROOT));
    }

    @Test(description = "verify reports the violation with the contract naming the layer and forbidden packages")
    public void verifyReportsViolationWithContract() {
        assertThatThrownBy(() -> new ProjectLayerBoundaryRule().verify(FixtureContexts.of(INVALID_ROOT)))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("A class in " + INVALID_ROOT + ".domain must not depend on")
                .hasMessageContaining(INVALID_ROOT + ".persistence")
                .hasMessageContaining(INVALID_ROOT + ".web")
                .hasMessageContaining("OrderPricing")
                .hasMessageContaining("OrderArchive");
    }

    @Test(description = "A correctly layered project passes verify")
    public void verifyPassesForCorrectLayering() {
        assertThatCode(() -> new ProjectLayerBoundaryRule().verify(FixtureContexts.of(VALID_ROOT)))
                .doesNotThrowAnyException();
    }

    @Test(description = "A project with no class in the layer package cannot be verified")
    public void projectWithoutLayerIsUnableToVerify() {
        assertThatThrownBy(() -> new ProjectLayerBoundaryRule().verify(FixtureContexts.of(NO_LAYER_ROOT)))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining(NO_LAYER_ROOT + ".domain");
    }

    @Test(description = "A violation that exists only in the test classes is reported")
    public void verifyReportsViolationInTestClasses() {
        ProjectLayerBoundaryRule rule = new ProjectLayerBoundaryRule();
        ArchUnitContext context = testOnly(INVALID_ROOT);

        assertThat(rule.scopesToCheck(context)).containsExactly(Scope.TEST);
        assertThatThrownBy(() -> rule.verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("OrderPricing");
    }

    @Test(description = "An empty main view is reported as skipped, not as a failure")
    public void emptyMainScopeIsSkipped() {
        ProjectLayerBoundaryRule rule = new ProjectLayerBoundaryRule();
        ArchUnitContext context = testOnly(VALID_ROOT);

        assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
        assertThat(rule.skippedScopes(context)).containsExactly(Scope.MAIN);
    }

    @Test(description = "The rule inspects main and test classes with error severity")
    public void ruleInspectsAllClassesWithErrorSeverity() {
        assertThat(new ProjectLayerBoundaryRule().scopes()).isEqualTo(Set.of(Scope.MAIN, Scope.TEST));
        assertThat(new ProjectLayerBoundaryRule().severity()).isEqualTo(Severity.ERROR);
        assertThat(new ProjectLayerBoundaryRule().contextType()).isEqualTo(ArchUnitContext.class);
    }

    @Test(description = "The name lists the layer and the forbidden packages in a stable order")
    public void nameListsLayerAndForbiddenPackages() {
        assertThat(new ProjectLayerBoundaryRule().name())
                .isEqualTo("Layer boundary (domain must not depend on persistence, web)");
    }

    @Test(description = "A blank layer package or an empty set of forbidden packages is rejected")
    public void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> new LayerBoundaryRule(" ", FORBIDDEN) {}).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LayerBoundaryRule(LAYER, Set.of()) {})
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LayerBoundaryRule(LAYER, Set.of("")) {})
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LayerBoundaryRule(null, FORBIDDEN) {}).isInstanceOf(NullPointerException.class);
    }

    @Test(description = "A forbidden package equal to, inside or around the layer package is rejected")
    public void overlappingPackagesAreRejected() {
        assertThatThrownBy(() -> new LayerBoundaryRule(LAYER, Set.of(LAYER)) {})
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps the layer package domain");
        assertThatThrownBy(() -> new LayerBoundaryRule(LAYER, Set.of("domain.internal")) {})
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("domain.internal");
        assertThatThrownBy(() -> new LayerBoundaryRule("core.domain", Set.of("core")) {})
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden package core overlaps");
        assertThatCode(() -> new LayerBoundaryRule(LAYER, Set.of("domainx")) {}).doesNotThrowAnyException();
    }

    @Test(description = "A package name with a leading or trailing dot is rejected")
    public void edgeDotsAreRejected() {
        assertThatThrownBy(() -> new LayerBoundaryRule(".domain", FORBIDDEN) {})
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("layer package .domain must not start or end with a dot");
        assertThatThrownBy(() -> new LayerBoundaryRule(LAYER, Set.of("web.")) {})
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden package web. must not start or end with a dot");
    }
}
