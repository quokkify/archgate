package dev.quokkify.archgate.rules.archunit.tms;

import static com.tngtech.archunit.base.DescribedPredicate.not;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.lang.syntax.elements.GivenClassesConjunction;
import dev.quokkify.archgate.context.archunit.support.GeneratedCode;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class TestExclusions {

    private TestExclusions() {}

    static Map<String, String> packages(Map<String, String> exclusions) {
        return select(exclusions, TestExclusions::isPackagePattern);
    }

    static Map<String, String> annotations(Map<String, String> exclusions) {
        return select(exclusions, Predicate.not(TestExclusions::isPackagePattern));
    }

    static GivenClassesConjunction checkedClasses(Map<String, String> exclusions) {
        Map<String, String> excludedPackages = packages(exclusions);
        Map<String, String> excludedAnnotations = annotations(exclusions);
        GivenClassesConjunction checked = ArchRuleDefinition.classes().that(not(GeneratedCode.EXCLUDED));
        if (!excludedPackages.isEmpty()) {
            checked = checked.and(not(inPackages(excludedPackages)));
        }
        if (!excludedAnnotations.isEmpty()) {
            checked = checked.and(not(annotatedWithAny(excludedAnnotations)));
        }
        return checked;
    }

    static void rejectUnmatchedKeys(Map<String, String> exclusions, JavaClasses testClasses) {
        Set<String> unknownAnnotations = annotations(exclusions).keySet().stream()
                .filter(annotation -> testClasses.stream().noneMatch(javaClass -> carries(javaClass, annotation)))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!unknownAnnotations.isEmpty()) {
            throw new IllegalArgumentException("""
          Exclusion keys %s are neither package patterns (use '..' or '*') nor annotations found on any test \
          class or method""".formatted(unknownAnnotations));
        }
        Set<String> emptyPatterns = packages(exclusions).keySet().stream()
                .filter(pattern -> testClasses.stream().noneMatch(JavaClass.Predicates.resideInAPackage(pattern)))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!emptyPatterns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Exclusion package patterns %s match no test class; a misspelt pattern would exclude nothing"
                            .formatted(emptyPatterns));
        }
    }

    static DescribedPredicate<JavaClass> inPackages(Map<String, String> excludedPackages) {
        return JavaClass.Predicates.resideInAnyPackage(excludedPackages.keySet().toArray(String[]::new))
                .as("excluded packages %s".formatted(excludedPackages));
    }

    static DescribedPredicate<JavaClass> annotatedWithAny(Map<String, String> excludedAnnotations) {
        return DescribedPredicate.describe(
                "classes excluded by annotation %s".formatted(excludedAnnotations),
                javaClass -> excludedAnnotations.keySet().stream().anyMatch(javaClass::isAnnotatedWith));
    }

    private static boolean carries(JavaClass javaClass, String annotation) {
        return javaClass.isAnnotatedWith(annotation)
                || javaClass.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(annotation));
    }

    private static boolean isPackagePattern(String key) {
        return key.contains("..") || key.contains("*");
    }

    private static Map<String, String> select(Map<String, String> exclusions, Predicate<String> keyFilter) {
        return exclusions.entrySet().stream()
                .filter(exclusion -> keyFilter.test(exclusion.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
    }
}
