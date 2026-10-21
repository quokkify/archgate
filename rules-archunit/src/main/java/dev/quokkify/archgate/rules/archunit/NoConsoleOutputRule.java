package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.conditions.ArchConditions.accessFieldWhere;
import static com.tngtech.archunit.lang.conditions.ArchConditions.callMethodWhere;

import com.tngtech.archunit.core.domain.JavaFieldAccess;
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

public class NoConsoleOutputRule implements ArchitectureRule<ArchUnitContext> {

    private static final Set<String> CONSOLE_STREAMS = Set.of("out", "err");
    private static final String EXPECTED_CONTRACT = """
      Production code must not write to System.out, System.err, Throwable.printStackTrace() or \
      Thread.dumpStack(). Log through a logger instead.""";

    @Override
    public String name() {
        return "No console output";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.MAIN);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public void verify(ArchUnitContext context) {
        List<String> violations = ArchRuleChecks.violations(definition(), context.main());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return ArchRuleDefinition.noClasses()
                .that(not(GeneratedCode.EXCLUDED))
                .should(accessFieldWhere(describe(
                        "access System.out or System.err",
                        (JavaFieldAccess access) ->
                                access.getTarget().getOwner().isEquivalentTo(System.class)
                                        && CONSOLE_STREAMS.contains(
                                                access.getTarget().getName()))))
                .orShould(callMethodWhere(describe(
                        "call Throwable.printStackTrace()",
                        (JavaMethodCall call) -> call.getTarget().getName().equals("printStackTrace")
                                && call.getTarget().getRawParameterTypes().isEmpty()
                                && call.getTarget().getOwner().isAssignableTo(Throwable.class))))
                .orShould(callMethodWhere(describe(
                        "call Thread.dumpStack()",
                        (JavaMethodCall call) -> call.getTarget().getName().equals("dumpStack")
                                && call.getTarget().getOwner().isEquivalentTo(Thread.class))))
                .allowEmptyShould(true)
                .because("console output bypasses the log configuration");
    }
}
