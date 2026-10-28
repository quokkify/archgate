package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;

import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NoThreadSleepRule implements ArchitectureRule<ArchUnitContext> {

    private static final String SLEEP = "sleep";
    private static final String EXPECTED_CONTRACT = """
      Code must not call Thread.sleep or TimeUnit.sleep. Wait for the awaited condition (for example with \
      Awaitility) instead of a fixed delay.""";

    @Override
    public String name() {
        return "No Thread.sleep";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
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
        List<String> violations = ArchRuleChecks.violations(definition(), context.main(), context.test());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return ArchRuleDefinition.noClasses()
                .that(not(GeneratedCode.EXCLUDED))
                .should(callMethodWhere(describe(
                        "call Thread.sleep or TimeUnit.sleep",
                        (JavaMethodCall call) -> call.getTarget().getName().equals(SLEEP)
                                && (call.getTarget().getOwner().isEquivalentTo(Thread.class)
                                        || call.getTarget().getOwner().isEquivalentTo(TimeUnit.class)))))
                .allowEmptyShould(true)
                .because("a fixed delay is either too short or wastes time");
    }
}
