package dev.quokkify.archgate.rules.archunit.api;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Flags use of the Jackson tree API ({@code JsonNode} and its subtypes, {@code JsonNodeFactory}, {@code readTree},
 * {@code valueToTree} and similar) in favour of typed models. Jackson types are matched by name, so this rule adds no
 * Jackson dependency.
 *
 * <p>The public constructor checks every test class. A subclass can choose the scopes, narrow the check to the
 * subtypes of some base classes, and allow named classes.
 */
public class JsonTreeUsageRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      Use typed models instead of the Jackson tree API (JsonNode, readTree, valueToTree, …) so request and response \
      shapes stay checked by the compiler.""";

    private static final String NAME = "No Jackson tree API";

    private static final String RESTRICTED_SUFFIX = " This applies to classes assignable to %s.";

    private final Set<Scope> scopes;
    private final Set<String> restrictedSupertypes;
    private final Map<String, String> allowedClassesWithReason;

    /**
     * Creates the rule over every test class, with no allowed classes.
     */
    public JsonTreeUsageRule() {
        this(EnumSet.of(Scope.TEST), Set.of(), Map.of());
    }

    /**
     * Called by subclasses that check other scopes, only some classes, or allow some classes.
     *
     * @param scopes the scopes to check; must not be empty
     * @param restrictedSupertypes fully qualified names of base types; when empty every class in the scopes is
     *     checked, otherwise only classes assignable to at least one of them
     * @param allowedClassesWithReason maps the fully qualified name of each class that may use the tree API to the
     *     justification for it; keys are matched exactly
     * @throws IllegalArgumentException if {@code scopes} is empty
     * @throws NullPointerException if an argument, an element, a key or a reason is {@code null}
     */
    protected JsonTreeUsageRule(
            Set<Scope> scopes, Set<String> restrictedSupertypes, Map<String, String> allowedClassesWithReason) {
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("The rule needs at least one scope to check");
        }
        this.scopes = Set.copyOf(scopes);
        this.restrictedSupertypes = Set.copyOf(restrictedSupertypes);
        this.allowedClassesWithReason = Map.copyOf(allowedClassesWithReason);
    }

    /**
     * Returns {@code No Jackson tree API}. When the check is narrowed to some supertypes, their simple names follow,
     * sorted and comma-separated, for instance {@code No Jackson tree API in BaseVerification, ProjectVerification}, so
     * that two registered variants can be told apart in the report.
     *
     * @return the rule name
     */
    @Override
    public String name() {
        if (restrictedSupertypes.isEmpty()) {
            return NAME;
        }
        return NAME + " in "
                + restrictedSupertypes.stream()
                        .map(type -> type.substring(type.lastIndexOf('.') + 1))
                        .sorted()
                        .collect(Collectors.joining(", "));
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Set<Scope> scopes() {
        return scopes;
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        String contract = restrictedSupertypes.isEmpty()
                ? EXPECTED_CONTRACT
                : EXPECTED_CONTRACT + RESTRICTED_SUFFIX.formatted(new TreeSet<>(restrictedSupertypes));
        checkViolations(
                contract,
                ArchRuleChecks.violations(
                        definition(restrictedSupertypes, allowedClassesWithReason), context, scopesToCheck(context)));
    }

    static ArchRule definition(Set<String> restrictedSupertypes, Map<String, String> allowed) {
        return ArchRuleDefinition.classes()
                .that(selected(restrictedSupertypes))
                .and(not(GeneratedCode.EXCLUDED))
                .and(not(allowlisted(allowed)))
                .should(new JacksonTreeUsageCondition())
                .allowEmptyShould(true)
                .because("typed models keep JSON shapes checked by the compiler");
    }

    private static DescribedPredicate<JavaClass> selected(Set<String> supertypes) {
        if (supertypes.isEmpty()) {
            return DescribedPredicate.alwaysTrue();
        }
        Set<String> sorted = new TreeSet<>(supertypes);
        return DescribedPredicate.describe(
                "are assignable to any of " + sorted,
                javaClass -> sorted.stream().anyMatch(javaClass::isAssignableTo));
    }

    private static DescribedPredicate<JavaClass> allowlisted(Map<String, String> allowed) {
        return DescribedPredicate.describe(
                "allowlisted Jackson tree boundaries " + new TreeSet<>(allowed.keySet()),
                javaClass -> allowed.containsKey(javaClass.getName()));
    }
}
