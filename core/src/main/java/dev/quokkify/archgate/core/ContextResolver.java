package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContextResolver implements AutoCloseable {

    private static final Pattern CONTEXT_MODULE = Pattern.compile("dev\\.quokkify\\.archgate\\.context\\.(\\w+)\\.");

    private final ProjectLayout layout;
    private final ClassLoader loader;
    private final Map<Class<?>, FutureTask<AnalysisContext>> contexts = new ConcurrentHashMap<>();
    private volatile Map<Class<?>, List<ContextProvider<?>>> providers;

    public ContextResolver(ProjectLayout layout, ClassLoader loader) {
        this.layout = layout;
        this.loader = loader;
    }

    public <C extends AnalysisContext> C get(Class<C> type) {
        FutureTask<AnalysisContext> task = contexts.computeIfAbsent(type, key -> new FutureTask<>(() -> create(type)));
        task.run();
        try {
            return type.cast(task.get());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UnableToVerifyError("Interrupted while building " + type.getName() + ".", interrupted);
        } catch (ExecutionException failed) {
            throw unableToBuild(type, failed.getCause());
        }
    }

    public Map<String, Duration> buildTimes() {
        Map<String, Duration> merged = new TreeMap<>();
        contexts.values().stream()
                .filter(FutureTask::isDone)
                .map(ContextResolver::completed)
                .filter(Objects::nonNull)
                .forEach(context -> merged.putAll(context.buildTimes()));
        return Map.copyOf(merged);
    }

    @Override
    public void close() {
        UnableToVerifyError failure = null;
        for (FutureTask<AnalysisContext> task : contexts.values()) {
            AnalysisContext context = completed(task);
            if (context instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    if (Objects.isNull(failure)) {
                        failure = new UnableToVerifyError("Failed to close analysis contexts.");
                    }
                    failure.addSuppressed(e);
                }
            }
        }
        if (Objects.nonNull(failure)) {
            throw failure;
        }
    }

    private AnalysisContext create(Class<? extends AnalysisContext> type) {
        List<ContextProvider<?>> candidates = providers().getOrDefault(type, List.of());
        if (candidates.isEmpty()) {
            throw new UnableToVerifyError(missingMessage(type));
        }
        if (candidates.size() > 1) {
            String names = String.join(
                    ", ",
                    candidates.stream()
                            .map(provider -> provider.getClass().getName())
                            .toList());
            throw new UnableToVerifyError("More than one ContextProvider for " + type.getName() + ": " + names + ".");
        }
        return candidates.get(0).create(layout);
    }

    private Map<Class<?>, List<ContextProvider<?>>> providers() {
        Map<Class<?>, List<ContextProvider<?>>> loaded = providers;
        if (Objects.isNull(loaded)) {
            synchronized (this) {
                loaded = providers;
                if (Objects.isNull(loaded)) {
                    loaded = index();
                    providers = loaded;
                }
            }
        }
        return loaded;
    }

    private Map<Class<?>, List<ContextProvider<?>>> index() {
        Map<Class<?>, List<ContextProvider<?>>> byType = new HashMap<>();
        List<ContextProvider<?>> found = new ArrayList<>();
        try {
            ServiceLoader.load(ContextProvider.class, loader).forEach(found::add);
        } catch (java.util.ServiceConfigurationError e) {
            throw new UnableToVerifyError("Cannot load context providers: " + e.getMessage(), e);
        }
        found.forEach(provider -> byType.computeIfAbsent(provider.type(), key -> new ArrayList<>())
                .add(provider));
        return byType;
    }

    private static String missingMessage(Class<?> type) {
        String message = "No ContextProvider for " + type.getName() + " is registered on the classpath.";
        Matcher module = CONTEXT_MODULE.matcher(type.getName());
        if (module.find()) {
            message += " Add dev.quokkify.archgate:archgate-context-" + module.group(1) + " as a test dependency.";
        }
        return message;
    }

    private static UnableToVerifyError unableToBuild(Class<?> type, Throwable cause) {
        if (cause instanceof UnableToVerifyError error) {
            return error;
        }
        return new UnableToVerifyError("Cannot build " + type.getName() + ": " + cause, cause);
    }

    private static AnalysisContext completed(FutureTask<AnalysisContext> task) {
        try {
            return task.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException failed) {
            return null;
        }
    }
}
