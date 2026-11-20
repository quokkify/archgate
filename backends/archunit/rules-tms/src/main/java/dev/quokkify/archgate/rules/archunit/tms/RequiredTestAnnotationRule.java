package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base for rules that demand one specific annotation on each TestNG test method. Only an annotation placed on the
 * method itself satisfies the rule; one on the class or on a sibling method is ignored.
 *
 * <p>Extend it in the consuming project, hand the {@link RequiredAnnotation} and the exclusions to the constructor,
 * and register the subclass in that project's {@code META-INF/services/dev.quokkify.archgate.api.ArchitectureRule}.
 * Only subclasses are registered, never this abstract base.
 */
public abstract class RequiredTestAnnotationRule implements ArchitectureRule<ArchUnitContext> {

    private final RequiredAnnotation annotation;
    private final Map<String, String> exclusionsWithReason;

    /**
     * Called by subclasses to configure the rule.
     *
     * @param annotation what each test method has to carry; passing {@code null} makes the rule fail when first used
     * @param exclusionsWithReason maps each exclusion key to the justification for it. A key with {@code ..} or
     *     {@code *} is read as an ArchUnit package pattern; any other key names an annotation, and a class or method
     *     bearing it is skipped. The map is defensively copied.
     * @throws NullPointerException if the map, or any key or reason in it, is {@code null}
     */
    protected RequiredTestAnnotationRule(RequiredAnnotation annotation, Map<String, String> exclusionsWithReason) {
        this.annotation = annotation;
        this.exclusionsWithReason = Map.copyOf(exclusionsWithReason);
    }

    @Override
    public String name() {
        return annotation.simpleName() + " on test methods";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.TEST);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        JavaClasses testClasses = context.test();
        TestExclusions.rejectUnmatchedKeys(exclusionsWithReason, testClasses);
        List<String> violations = ArchRuleChecks.violations(definition(annotation, exclusionsWithReason), testClasses);
        checkViolations(expectedContract(), violations);
    }

    /**
     * Returns the sentence that the violation report prints as the expected behaviour.
     *
     * @return the expectation, in one or two sentences
     */
    protected String expectedContract() {
        return "A TestNG test method needs @" + annotation.name()
                + (annotation.nonBlankValue() ? " with a non-blank value" : "")
                + " on the method itself; the class or a sibling method having it is not enough.";
    }

    static ArchRule definition(RequiredAnnotation annotation, Map<String, String> exclusions) {
        return TestExclusions.checkedClasses(exclusions)
                .should(new RequiredAnnotationCondition(
                        annotation, TestExclusions.annotations(exclusions).keySet()))
                .allowEmptyShould(true)
                .because("each test method has to be marked on its own");
    }
}
