package dev.quokkify.archgate.rules.archunit;

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
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoJavaUtilDateRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      Code must not depend on java.util.Date. Use java.time types (Instant, LocalDateTime, ZonedDateTime) \
      instead.""";

    private final Map<String, String> allowedClassesWithReason;

    public NoJavaUtilDateRule() {
        this(Map.of());
    }

    protected NoJavaUtilDateRule(Map<String, String> allowedClassesWithReason) {
        this.allowedClassesWithReason = Map.copyOf(allowedClassesWithReason);
    }

    @Override
    public String name() {
        return "No java.util.Date";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN, Scope.TEST);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        List<String> violations =
                ArchRuleChecks.violations(definition(allowedClassesWithReason), context.main(), context.test());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return definition(Map.of());
    }

    static ArchRule definition(Map<String, String> allowed) {
        return ArchRuleDefinition.noClasses()
                .that(not(GeneratedCode.EXCLUDED))
                .and(not(allowlisted(allowed)))
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(Date.class.getName())
                .allowEmptyShould(true)
                .because("java.util.Date is mutable and has no time zone");
    }

    private static DescribedPredicate<JavaClass> allowlisted(Map<String, String> allowed) {
        return DescribedPredicate.describe(
                "allowlisted java.util.Date boundaries " + allowed.keySet(),
                javaClass -> allowed.containsKey(javaClass.getName()));
    }
}
