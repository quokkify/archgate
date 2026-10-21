package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvent;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
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

public class NoDeprecatedApiUsageRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      Code must not use anything annotated @Deprecated — fields, methods, constructors, superclasses or the \
      deprecated classes themselves. The next upgrade can remove them; replace the usage with the \
      supported alternative from the @Deprecated documentation.""";

    @Override
    public String name() {
        return "No deprecated API usage";
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
        List<String> violations = ArchRuleChecks.violations(definition(), context.main(), context.test());
        checkViolations(EXPECTED_CONTRACT, violations);
    }

    static ArchRule definition() {
        return ArchRuleDefinition.classes()
                .that(not(GeneratedCode.EXCLUDED))
                .should(notUseDeprecatedApi())
                .allowEmptyShould(true)
                .because("deprecated APIs disappear on the next dependency upgrade");
    }

    private static ArchCondition<JavaClass> notUseDeprecatedApi() {
        return new ArchCondition<>("not use deprecated APIs") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getFieldAccessesFromSelf().stream()
                        .filter(access -> access.getTarget().isAnnotatedWith(Deprecated.class))
                        .forEach(access -> events.add(violation(
                                access.getOrigin().getFullName(),
                                "accesses deprecated field %s"
                                        .formatted(access.getTarget().getFullName()))));
                javaClass.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTarget().isAnnotatedWith(Deprecated.class)
                                || call.getTarget().getRawReturnType().isAnnotatedWith(Deprecated.class)
                                || call.getTarget().getRawParameterTypes().stream()
                                        .anyMatch(NoDeprecatedApiUsageRule::deprecatedErasure))
                        .forEach(call -> events.add(violation(
                                call.getOrigin().getFullName(),
                                "calls deprecated %s".formatted(call.getTarget().getFullName()))));
                javaClass.getConstructorCallsFromSelf().stream()
                        .filter(call -> call.getTarget().isAnnotatedWith(Deprecated.class)
                                || call.getTarget().getRawParameterTypes().stream()
                                        .anyMatch(NoDeprecatedApiUsageRule::deprecatedErasure))
                        .forEach(call -> events.add(violation(
                                call.getOrigin().getFullName(),
                                "calls deprecated constructor %s"
                                        .formatted(call.getTarget().getFullName()))));
                javaClass.getAllRawSuperclasses().stream()
                        .filter(superclass -> superclass.isAnnotatedWith(Deprecated.class))
                        .forEach(superclass -> events.add(violation(
                                javaClass.getName(), "extends deprecated %s".formatted(superclass.getName()))));
                javaClass.getInterfaces().stream()
                        .filter(iface -> iface.toErasure().isAnnotatedWith(Deprecated.class))
                        .forEach(iface -> events.add(violation(
                                javaClass.getName(),
                                "implements deprecated %s"
                                        .formatted(iface.toErasure().getName()))));
            }

            private ConditionEvent violation(String origin, String detail) {
                return SimpleConditionEvent.violated(origin, "%s %s".formatted(origin, detail));
            }
        };
    }

    private static boolean deprecatedErasure(JavaType type) {
        return type.toErasure().isAnnotatedWith(Deprecated.class);
    }
}
