package dev.quokkify.archgate.rules.archunit.api;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base for keeping one package layer free of dependencies on the layers above it, for packages that share a module
 * and therefore a classpath, where the build tool cannot separate them.
 *
 * <p>Packages are given relative to the context's root package. A subclass names the lower layer and the packages it
 * must not depend on, for instance {@code "domain"} and {@code Set.of("persistence", "web")}; the rule then reports
 * every class under {@code <root>.domain} that depends on a class under {@code <root>.persistence} or
 * {@code <root>.web}. A layer package with no class in the checked views is
 * reported as unable to verify, since a misspelt package would otherwise pass silently.
 */
public abstract class LayerBoundaryRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      A class in %s must not depend on %s. Those packages sit above it, so a dependency back into them inverts \
      the layering and creates a cycle between the layers.""";

    private final String layerPackage;
    private final List<String> forbiddenPackages;

    /**
     * Called by subclasses to name the layer and the packages it must not depend on.
     *
     * @param layerPackage the lower layer, relative to the root package, for example {@code "domain"}
     * @param forbiddenPackages the packages the layer must not depend on, relative to the root package, for example
     *     {@code Set.of("persistence", "web")}
     * @throws IllegalArgumentException if {@code layerPackage} is blank, {@code forbiddenPackages} is empty, a name is
     *     blank or starts or ends with a dot, or a forbidden package equals the layer package, lies inside it or
     *     contains it
     * @throws NullPointerException if an argument or an element is {@code null}
     */
    protected LayerBoundaryRule(String layerPackage, Set<String> forbiddenPackages) {
        Objects.requireNonNull(layerPackage, "layerPackage");
        if (layerPackage.isBlank()) {
            throw new IllegalArgumentException("The layer package must not be blank");
        }
        if (forbiddenPackages.isEmpty()) {
            throw new IllegalArgumentException("At least one forbidden package is required");
        }
        if (forbiddenPackages.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("A forbidden package must not be blank");
        }
        rejectEdgeDots("layer package", layerPackage);
        for (String forbidden : forbiddenPackages) {
            rejectEdgeDots("forbidden package", forbidden);
            if (overlaps(layerPackage, forbidden)) {
                throw new IllegalArgumentException(
                        "The forbidden package %s overlaps the layer package %s: one must not equal or contain the other"
                                .formatted(forbidden, layerPackage));
            }
        }
        this.layerPackage = layerPackage;
        this.forbiddenPackages = forbiddenPackages.stream().sorted().toList();
    }

    @Override
    public String name() {
        return "Layer boundary (%s must not depend on %s)"
                .formatted(layerPackage, String.join(", ", forbiddenPackages));
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnableToVerifyError if no class of the checked views lives in the layer package
     */
    @Override
    public void verify(ArchUnitContext context) {
        String root = context.rootPackage();
        List<Scope> scopes = scopesToCheck(context);
        DescribedPredicate<JavaClass> inLayer = JavaClass.Predicates.resideInAPackage(pattern(root, layerPackage));
        boolean layerPresent = scopes.stream()
                .map(context::classes)
                .anyMatch(classes -> classes.stream().anyMatch(inLayer));
        if (!layerPresent) {
            throw new UnableToVerifyError("""
          No class in %s was found in the checked views (%s), so the boundary of that layer cannot be checked. \
          Check the layer package passed to the rule.""".formatted(qualified(root, layerPackage), names(scopes)));
        }
        List<String> qualifiedForbidden =
                forbiddenPackages.stream().map(name -> qualified(root, name)).toList();
        checkViolations(
                EXPECTED_CONTRACT.formatted(qualified(root, layerPackage), String.join(" or ", qualifiedForbidden)),
                ArchRuleChecks.violations(definition(root, layerPackage, forbiddenPackages), context, scopes));
    }

    static ArchRule definition(String rootPackage, String layerPackage, Collection<String> forbiddenPackages) {
        String[] forbidden = forbiddenPackages.stream()
                .sorted()
                .map(name -> pattern(rootPackage, name))
                .toArray(String[]::new);
        return ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(pattern(rootPackage, layerPackage))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forbidden)
                .allowEmptyShould(true)
                .because("a lower layer must never depend on the layers above it");
    }

    private static void rejectEdgeDots(String role, String name) {
        if (name.startsWith(".") || name.endsWith(".")) {
            throw new IllegalArgumentException(
                    "The %s %s must not start or end with a dot; give it relative to the root package, such as domain"
                            .formatted(role, name));
        }
    }

    private static boolean overlaps(String first, String second) {
        return first.equals(second) || first.startsWith(second + ".") || second.startsWith(first + ".");
    }

    private static String pattern(String rootPackage, String relativePackage) {
        return qualified(rootPackage, relativePackage) + "..";
    }

    private static String qualified(String rootPackage, String relativePackage) {
        return rootPackage.isEmpty() ? relativePackage : rootPackage + "." + relativePackage;
    }

    private static String names(List<Scope> scopes) {
        return scopes.stream()
                .map(scope -> scope.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }
}
