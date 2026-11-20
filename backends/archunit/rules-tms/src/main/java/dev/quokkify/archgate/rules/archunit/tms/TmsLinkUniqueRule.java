package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Reports a {@code @TmsLink} case id that is placed on more than one TestNG test method. */
public class TmsLinkUniqueRule implements ArchitectureRule<ArchUnitContext> {

    private static final String EXPECTED_CONTRACT = """
      Every @io.qameta.allure.TmsLink case id must identify a single TestNG test method; \
      one method may still carry several distinct case ids.""";

    private static final Comparator<String> BY_ID =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    private final Map<String, String> exclusionsWithReason;

    /**
     * Creates the rule with no exclusions; any case id that appears on two or more test methods is reported.
     */
    public TmsLinkUniqueRule() {
        this(Map.of());
    }

    /**
     * Called by subclasses to leave some tests out of the comparison.
     *
     * @param exclusionsWithReason maps each exclusion key to the justification for it. A key with {@code ..} or
     *     {@code *} is read as an ArchUnit package pattern; any other key names an annotation, and a class or method
     *     bearing it is skipped. The map is defensively copied.
     * @throws NullPointerException if the map, or any key or reason in it, is {@code null}
     */
    protected TmsLinkUniqueRule(Map<String, String> exclusionsWithReason) {
        this.exclusionsWithReason = Map.copyOf(exclusionsWithReason);
    }

    /**
     * Decides whether an id repeated only inside a single class is tolerated, which suits one scenario deliberately
     * split across several methods. An id that also turns up in a second class is always reported.
     *
     * @return {@code true} to tolerate repeats inside one class; this base returns {@code false}
     */
    protected boolean allowDuplicatesWithinClass() {
        return false;
    }

    @Override
    public String name() {
        return "Unique TmsLink ids on test methods";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.TEST);
    }

    @Override
    public void verify(ArchUnitContext context) {
        JavaClasses testClasses = context.test();
        TestExclusions.rejectUnmatchedKeys(exclusionsWithReason, testClasses);
        checkViolations(EXPECTED_CONTRACT, violations(testClasses, exclusionsWithReason, allowDuplicatesWithinClass()));
    }

    static List<String> violations(
            JavaClasses testClasses, Map<String, String> exclusions, boolean allowDuplicatesWithinClass) {
        Set<String> excludedAnnotations = TestExclusions.annotations(exclusions).keySet();
        Predicate<JavaClass> skipped = skippedClasses(exclusions);
        Map<String, List<JavaMethod>> methodsById = new TreeMap<>(BY_ID);
        testClasses.stream()
                .filter(Predicate.not(skipped))
                .flatMap(javaClass -> javaClass.getMethods().stream())
                .filter(TestMethods::isTestMethod)
                .filter(method -> excludedAnnotations.stream().noneMatch(method::isAnnotatedWith))
                .forEach(method -> TmsLinks.ids(method)
                        .forEach(id -> methodsById
                                .computeIfAbsent(id, key -> new ArrayList<>())
                                .add(method)));
        return methodsById.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .filter(entry -> !(allowDuplicatesWithinClass && declaredByOneClass(entry.getValue())))
                .map(entry -> describe(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Predicate<JavaClass> skippedClasses(Map<String, String> exclusions) {
        DescribedPredicate<JavaClass> skipped = GeneratedCode.EXCLUDED;
        Map<String, String> excludedPackages = TestExclusions.packages(exclusions);
        Map<String, String> excludedAnnotations = TestExclusions.annotations(exclusions);
        if (!excludedPackages.isEmpty()) {
            skipped = skipped.or(TestExclusions.inPackages(excludedPackages));
        }
        if (!excludedAnnotations.isEmpty()) {
            skipped = skipped.or(TestExclusions.annotatedWithAny(excludedAnnotations));
        }
        return skipped;
    }

    private static boolean declaredByOneClass(List<JavaMethod> methods) {
        return methods.stream().map(JavaMethod::getOwner).distinct().count() == 1;
    }

    private static String describe(String id, List<JavaMethod> methods) {
        String sharing = methods.stream()
                .sorted(Comparator.comparing(JavaMethod::getFullName))
                .map(method -> "<%s> in %s".formatted(method.getFullName(), method.getSourceCodeLocation()))
                .collect(Collectors.joining(", "));
        return "TmsLink id <%s> is shared by %d test methods: %s".formatted(id, methods.size(), sharing);
    }
}
