package dev.quokkify.archgate.cases.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/** The case ids linked from test methods through {@code @TmsLink}, each with its methods sorted by full name. */
final class LinkedCases {

    private final SortedMap<Integer, List<JavaMethod>> byId;
    private final boolean changedFilesOnly;

    private LinkedCases(SortedMap<Integer, List<JavaMethod>> byId, boolean changedFilesOnly) {
        this.byId = byId;
        this.changedFilesOnly = changedFilesOnly;
    }

    static LinkedCases from(JavaClasses testClasses) {
        return new LinkedCases(collect(testClasses, javaClass -> true), false);
    }

    /** Links of the test classes declared in a file of {@code changed}. */
    static LinkedCases from(JavaClasses testClasses, ChangedFiles changed) {
        Objects.requireNonNull(changed, "changed");
        return new LinkedCases(collect(testClasses, changed::contains), true);
    }

    /** Whether only the test classes of changed files were scanned. */
    boolean changedFilesOnly() {
        return changedFilesOnly;
    }

    SortedMap<Integer, List<JavaMethod>> byId() {
        return byId;
    }

    SortedSet<Integer> ids() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(byId.keySet()));
    }

    boolean isEmpty() {
        return byId.isEmpty();
    }

    private static SortedMap<Integer, List<JavaMethod>> collect(JavaClasses testClasses, Predicate<JavaClass> keep) {
        SortedMap<Integer, List<JavaMethod>> byId = new TreeMap<>();
        for (JavaClass javaClass : testClasses) {
            if (!keep.test(javaClass)) {
                continue;
            }
            for (JavaMethod method : javaClass.getMethods()) {
                if (TestMethods.isTestMethod(method)) {
                    ids(method)
                            .forEach(id -> byId.computeIfAbsent(id, key -> new ArrayList<>())
                                    .add(method));
                }
            }
        }
        byId.replaceAll((id, methods) -> methods.stream()
                .sorted(Comparator.comparing(JavaMethod::getFullName))
                .toList());
        return Collections.unmodifiableSortedMap(byId);
    }

    private static SortedSet<Integer> ids(JavaMethod method) {
        SortedSet<Integer> ids = new TreeSet<>();
        TmsLinks.values(method).stream()
                .map(LinkedCases::normalize)
                .flatMap(Optional::stream)
                .forEach(ids::add);
        return ids;
    }

    /** Reads {@code C123}, {@code c123} or {@code 123}, surrounded by blanks; anything else links no case. */
    private static Optional<Integer> normalize(String raw) {
        String text = raw.strip();
        if (text.startsWith("C") || text.startsWith("c")) {
            text = text.substring(1);
        }
        if (text.isEmpty() || !text.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            return Optional.empty();
        }
        try {
            int id = Integer.parseInt(text);
            return id == 0 ? Optional.empty() : Optional.of(id);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
