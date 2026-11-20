package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.RunServices;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * The {@link RunServices} of one gate run: the {@link VariableEnvironment} and the values rules share.
 *
 * <p>Each instance keeps its own shared values, so one instance serves exactly one gate run.
 */
public final class GateRunServices implements RunServices {

    private static final ThreadLocal<SharedClock> CLOCK = ThreadLocal.withInitial(SharedClock::new);

    private final VariableEnvironment environment;
    private final Map<String, FutureTask<Object>> values = new ConcurrentHashMap<>();
    private final Map<String, Duration> buildTimes = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<String>> building = new ThreadLocal<>();

    /**
     * Creates the services of one run.
     *
     * @param environment the environment every context of the run shares
     */
    public GateRunServices(VariableEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    /**
     * Returns the environment and records that a rule touched it, so a later run does not reuse this one.
     *
     * @return the environment every context of the run shares
     */
    @Override
    public VariableEnvironment environment() {
        environment.markRead();
        return environment;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T shared(String name, Supplier<T> build) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(build, "build");
        Set<String> inProgress = building.get();
        if (Objects.nonNull(inProgress) && inProgress.contains(name)) {
            throw new UnableToVerifyError(
                    "Shared value '%s' is requested again while it is being built, so it can never be built."
                            .formatted(name));
        }
        FutureTask<Object> task = values.computeIfAbsent(name, key -> new FutureTask<>(() -> build(key, build)));
        SharedClock clock = CLOCK.get();
        boolean outermost = clock.depth == 0;
        long startedAt = System.nanoTime();
        clock.depth++;
        try {
            task.run();
            return (T) task.get();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new UnableToVerifyError("Shared value '%s' could not be built: %s".formatted(name, cause), cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UnableToVerifyError(
                    "Interrupted while waiting for shared value '%s'.".formatted(name), interrupted);
        } finally {
            clock.depth--;
            if (outermost) {
                clock.nanos += System.nanoTime() - startedAt;
            }
        }
    }

    /**
     * Returns how long each shared value took to build, including builds that failed.
     *
     * @return build durations by name, sorted by name
     */
    public Map<String, Duration> sharedBuildTimes() {
        return Collections.unmodifiableMap(new TreeMap<>(buildTimes));
    }

    /**
     * Returns the time the current thread spent inside {@link #shared(String, Supplier)} since the last call,
     * and starts counting again from zero. A call made while another is in progress on the same thread counts
     * once, as part of the outer one.
     *
     * @return nanoseconds spent inside {@code shared} on this thread
     */
    static long takeTimeInShared() {
        SharedClock clock = CLOCK.get();
        long nanos = clock.nanos;
        clock.nanos = 0;
        return nanos;
    }

    private Object build(String name, Supplier<?> build) {
        Set<String> names = building.get();
        if (Objects.isNull(names)) {
            names = new HashSet<>();
            building.set(names);
        }
        names.add(name);
        long startedAt = System.nanoTime();
        try {
            return build.get();
        } finally {
            buildTimes.put(name, Duration.ofNanos(System.nanoTime() - startedAt));
            names.remove(name);
            if (names.isEmpty()) {
                building.remove();
            }
        }
    }

    private static final class SharedClock {
        private int depth;
        private long nanos;
    }
}
