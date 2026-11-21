package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.conditions.ArchConditions.accessTargetWhere;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class NoThreadSleepRule implements ArchitectureRule<ArchUnitContext> {

    private static final String SLEEP = "sleep";
    private static final Set<List<String>> THREAD_SLEEP_SIGNATURES =
            Set.of(List.of("long"), List.of("long", "int"), List.of(Duration.class.getName()));
    private static final List<String> TIME_UNIT_SLEEP_SIGNATURE = List.of("long");
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
        List<String> violations = ArchRuleChecks.violations(definition(), context, scopesToCheck(context));
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return ArchRuleDefinition.noClasses()
                .that(not(GeneratedCode.EXCLUDED))
                .should(accessTargetWhere(
                        describe("call or reference Thread.sleep or TimeUnit.sleep", NoThreadSleepRule::sleeps)))
                .allowEmptyShould(true)
                .because("a fixed delay is either too short or wastes time");
    }

    // The owner is matched by assignability because javac names a subclass as owner of an inherited method;
    // the signature keeps methods that a subclass declares itself under the same name out.
    private static boolean sleeps(JavaAccess<?> access) {
        if (!(access instanceof JavaMethodCall || access instanceof JavaMethodReference)
                || !access.getName().equals(SLEEP)) {
            return false;
        }
        List<String> parameters = ((JavaCodeUnitAccess<?>) access)
                .getTarget().getRawParameterTypes().stream()
                        .map(JavaClass::getName)
                        .toList();
        JavaClass owner = access.getTargetOwner();
        return (owner.isAssignableTo(Thread.class) && THREAD_SLEEP_SIGNATURES.contains(parameters))
                || (owner.isAssignableTo(TimeUnit.class) && TIME_UNIT_SLEEP_SIGNATURE.equals(parameters));
    }
}
