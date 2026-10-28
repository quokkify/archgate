package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaField;
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

public class LoggerFieldNameRule implements ArchitectureRule<ArchUnitContext> {

    private static final String DEFAULT_FIELD_NAME = "LOG";
    private static final Set<String> LOGGER_TYPES = Set.of("org.apache.logging.log4j.Logger", "org.slf4j.Logger");

    private final String fieldName;

    public LoggerFieldNameRule() {
        this(DEFAULT_FIELD_NAME);
    }

    protected LoggerFieldNameRule(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String name() {
        return "Logger field name";
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
        List<String> violations = ArchRuleChecks.violations(definition(fieldName), context.main());
        checkViolations(expectedContract(fieldName), violations);
    }

    static ArchRule definition() {
        return definition(DEFAULT_FIELD_NAME);
    }

    static ArchRule definition(String fieldName) {
        return ArchRuleDefinition.fields()
                .that(loggerType())
                .and()
                .areDeclaredInClassesThat(not(GeneratedCode.EXCLUDED))
                .should()
                .haveName(fieldName)
                .andShould()
                .beStatic()
                .andShould()
                .beFinal()
                .allowEmptyShould(true)
                .because("a uniform logger field keeps log statements consistent");
    }

    private static String expectedContract(String fieldName) {
        return "A Log4j2 or SLF4J Logger field must be named '%s' and be declared 'static final', for example "
                        .formatted(fieldName)
                + "'private static final Logger %s = LogManager.getLogger(Foo.class)'. ".formatted(fieldName)
                + "Lombok @Log4j2 and @Slf4j generate a field named 'log' and are not allowed.";
    }

    private static DescribedPredicate<JavaField> loggerType() {
        return DescribedPredicate.describe(
                "have a Log4j2 or SLF4J logger type",
                field -> LOGGER_TYPES.contains(field.getRawType().getName()));
    }
}
