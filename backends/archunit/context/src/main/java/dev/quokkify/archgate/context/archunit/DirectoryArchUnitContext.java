package dev.quokkify.archgate.context.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

final class DirectoryArchUnitContext implements ArchUnitContext {

    private static final String ALL_MODEL = "all";
    private static final String MAIN_MODEL = "main";
    private static final String TEST_MODEL = "test";

    private final Map<String, Duration> buildTimes = new ConcurrentSkipListMap<>();

    private final Object allLock = new Object();
    private final Object mainLock = new Object();
    private final Object testLock = new Object();

    private final ProjectLayout layout;
    private final String rootPackage;
    private final List<Path> mainDirs;
    private final List<Path> testDirs;

    private volatile JavaClasses all;
    private volatile JavaClasses main;
    private volatile JavaClasses test;

    DirectoryArchUnitContext(ProjectLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.rootPackage = Objects.requireNonNull(layout.rootPackage(), "rootPackage");
        this.mainDirs = List.copyOf(layout.classDirs(Scope.MAIN));
        this.testDirs = List.copyOf(layout.classDirs(Scope.TEST));
    }

    @Override
    public String rootPackage() {
        return rootPackage;
    }

    @Override
    public ProjectLayout layout() {
        return layout;
    }

    @Override
    public JavaClasses main() {
        synchronized (mainLock) {
            if (Objects.isNull(main)) {
                main = built(MAIN_MODEL, () -> importDirs("main", mainDirs));
            }
            return main;
        }
    }

    @Override
    public JavaClasses test() {
        synchronized (testLock) {
            if (Objects.isNull(test)) {
                test = built(TEST_MODEL, () -> importDirs("test", testDirs));
            }
            return test;
        }
    }

    @Override
    public JavaClasses all() {
        synchronized (allLock) {
            if (Objects.isNull(all)) {
                all = built(ALL_MODEL, () -> {
                    List<Path> dirs = new ArrayList<>(mainDirs);
                    dirs.addAll(testDirs);
                    return importDirs("main or test", dirs);
                });
            }
            return all;
        }
    }

    @Override
    public Map<String, Duration> buildTimes() {
        return Collections.unmodifiableMap(new TreeMap<>(buildTimes));
    }

    private JavaClasses importDirs(String view, List<Path> configured) {
        List<Path> existing = configured.stream().filter(Files::isDirectory).toList();
        JavaClasses imported = existing.isEmpty()
                ? null
                : new ClassFileImporter()
                        .importPaths(existing)
                        .that(JavaClass.Predicates.resideInAPackage(rootPackage + ".."));
        if (Objects.isNull(imported) || imported.isEmpty()) {
            throw new UnableToVerifyError("""
          No class under %s was found in the %s class directories %s. Build the modules first, otherwise every \
          rule using this view would pass without checking anything.""".formatted(rootPackage, view, configured));
        }
        return imported;
    }

    private <T> T built(String model, Supplier<T> build) {
        long startedAt = System.nanoTime();
        T result = build.get();
        buildTimes.put(model, Duration.ofNanos(System.nanoTime() - startedAt));
        return result;
    }
}
