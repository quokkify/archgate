package dev.quokkify.archgate.context.classgraph;

import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import io.github.classgraph.ScanResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectoryClassGraphContext implements ClassGraphContext {

    private static final String SERVICES_PATH = "META-INF/services";
    private static final String BUILD_TIME_PREFIX = "classgraph ";

    private final Map<String, Duration> buildTimes = new ConcurrentSkipListMap<>();
    private final Map<Scope, FutureTask<ScanResult>> scans = new ConcurrentHashMap<>();
    private final Queue<ScanResult> opened = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private final ProjectLayout layout;
    private final String rootPackage;

    DirectoryClassGraphContext(ProjectLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.rootPackage = Objects.requireNonNull(layout.rootPackage(), "rootPackage");
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
    public ScanResult scan(Scope scope) {
        if (closed.get()) {
            throw new IllegalStateException("The ClassGraph context is closed");
        }
        FutureTask<ScanResult> task = scans.computeIfAbsent(scope, key -> new FutureTask<>(() -> scanScope(key)));
        task.run();
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UnableToVerifyError("Interrupted while scanning the " + label(scope) + " classes.", interrupted);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof UnableToVerifyError unable) {
                throw new UnableToVerifyError(unable.getMessage(), unable);
            }
            if (failed.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failed.getCause() instanceof Error error) {
                throw error;
            }
            throw new UnableToVerifyError("Cannot scan the " + label(scope) + " classes.", failed.getCause());
        }
    }

    @Override
    public Map<String, Duration> buildTimes() {
        return Collections.unmodifiableMap(new TreeMap<>(buildTimes));
    }

    @Override
    public void close() {
        closed.set(true);
        ScanResult result = opened.poll();
        while (Objects.nonNull(result)) {
            result.close();
            result = opened.poll();
        }
    }

    private ScanResult scanScope(Scope scope) {
        List<String> dirs = layout.classDirs(scope).stream()
                .filter(Files::isDirectory)
                .map(Path::toString)
                .toList();
        if (dirs.isEmpty()) {
            throw new UnableToVerifyError(
                    "No %s class directory exists among %s. Build the modules first, otherwise every rule using this view would pass without checking anything."
                            .formatted(label(scope), layout.classDirs(scope)));
        }
        long startedAt = System.nanoTime();
        ScanResult result;
        try {
            result = new ClassGraph()
                    .overrideClasspath(dirs)
                    .enableClassInfo()
                    .enableExternalClasses()
                    .acceptPackages(rootPackage)
                    .acceptPaths(SERVICES_PATH)
                    .scan();
        } catch (ClassGraphException e) {
            throw new UnableToVerifyError("Cannot scan " + dirs + ": " + e.getMessage(), e);
        }
        buildTimes.put(BUILD_TIME_PREFIX + label(scope), Duration.ofNanos(System.nanoTime() - startedAt));
        opened.add(result);
        if (closed.get()) {
            result.close();
        }
        return result;
    }

    private static String label(Scope scope) {
        return scope.name().toLowerCase(Locale.ROOT);
    }
}
