package dev.quokkify.archgate.rules.archunit;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassLocationBySuffixRule implements ArchitectureRule<ArchUnitContext> {

    private final Map<String, String> suffixToPackage;

    public ClassLocationBySuffixRule() {
        this(defaultMapping());
    }

    protected ClassLocationBySuffixRule(Map<String, String> suffixToPackage) {
        if (suffixToPackage.isEmpty()) {
            throw new IllegalArgumentException("At least one suffix to package mapping is required");
        }
        this.suffixToPackage = Collections.unmodifiableMap(new LinkedHashMap<>(suffixToPackage));
    }

    @Override
    public String name() {
        return "Class location by suffix";
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
        verify(context.main());
    }

    void verify(JavaClasses classes) {
        boolean anySuffixed = classes.stream()
                .anyMatch(javaClass -> suffixToPackage.keySet().stream().anyMatch(javaClass.getSimpleName()::endsWith));
        if (!anySuffixed) {
            throw new UnableToVerifyError("""
          No class is named %s, so class location by suffix was not verified at all. The main classes must be \
          built and on the verification classpath.""".formatted(suffixList()));
        }
        List<String> violations = ArchRuleChecks.violations(definition(), classes);
        checkViolations(expectedContract(), violations);
    }

    ArchRule definition() {
        CompositeArchRule composite = null;
        for (Map.Entry<String, String> entry : suffixToPackage.entrySet()) {
            ArchRule rule = locatedIn(entry.getKey(), entry.getValue());
            composite = Objects.isNull(composite) ? CompositeArchRule.of(rule) : composite.and(rule);
        }
        return composite;
    }

    private String expectedContract() {
        String mapping = suffixToPackage.entrySet().stream()
                .map(entry -> "*%s in '%s'".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
        return ("A top-level class (not an enum, interface or annotation) must reside in the package matching its "
                        + "suffix: %s. Move the class, or rename it if it is not of that kind.")
                .formatted(mapping);
    }

    private String suffixList() {
        return suffixToPackage.keySet().stream().map("*%s"::formatted).collect(Collectors.joining(", "));
    }

    private static Map<String, String> defaultMapping() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("Steps", "..steps..");
        mapping.put("Verification", "..verifications..");
        mapping.put("Verifications", "..verifications..");
        mapping.put("Generator", "..generators..");
        return mapping;
    }

    private static ArchRule locatedIn(String suffix, String packageIdentifier) {
        return ArchRuleDefinition.classes()
                .that(not(GeneratedCode.EXCLUDED))
                .and()
                .areTopLevelClasses()
                .and()
                .areNotEnums()
                .and()
                .areNotInterfaces()
                .and()
                .areNotAnnotations()
                .and()
                .haveSimpleNameEndingWith(suffix)
                .should()
                .resideInAPackage(packageIdentifier)
                .allowEmptyShould(true)
                .because("a class named *%s belongs in a '%s' package".formatted(suffix, packageIdentifier));
    }
}
