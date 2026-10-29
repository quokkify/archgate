package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.CustomContext;
import dev.quokkify.annotation.SingleThread;
import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

class ContextResolverTest {

    private static final AtomicInteger CREATED = new AtomicInteger();

    public record CountedContext() implements AnalysisContext {}

    public static final class CountingProvider implements ContextProvider<CountedContext> {

        @Override
        public Class<CountedContext> type() {
            return CountedContext.class;
        }

        @Override
        public CountedContext create(ProjectLayout layout) {
            CREATED.incrementAndGet();
            return new CountedContext();
        }
    }

    public static final class ClosableContext implements AnalysisContext, AutoCloseable {

        boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    public static final class ClosableProvider implements ContextProvider<ClosableContext> {

        @Override
        public Class<ClosableContext> type() {
            return ClosableContext.class;
        }

        @Override
        public ClosableContext create(ProjectLayout layout) {
            return new ClosableContext();
        }
    }

    public static final class FailingClosableContext implements AnalysisContext, AutoCloseable {

        @Override
        public void close() {
            throw new IllegalStateException("cannot close");
        }
    }

    public static final class FailingClosableProvider implements ContextProvider<FailingClosableContext> {

        @Override
        public Class<FailingClosableContext> type() {
            return FailingClosableContext.class;
        }

        @Override
        public FailingClosableContext create(ProjectLayout layout) {
            return new FailingClosableContext();
        }
    }

    public static final class TimedContext implements AnalysisContext {

        @Override
        public Map<String, Duration> buildTimes() {
            return Map.of("main", Duration.ofMillis(7));
        }
    }

    public static final class TimedProvider implements ContextProvider<TimedContext> {

        @Override
        public Class<TimedContext> type() {
            return TimedContext.class;
        }

        @Override
        public TimedContext create(ProjectLayout layout) {
            return new TimedContext();
        }
    }

    public static final class ThrowingContext implements AnalysisContext {}

    public static final class ThrowingProvider implements ContextProvider<ThrowingContext> {

        @Override
        public Class<ThrowingContext> type() {
            return ThrowingContext.class;
        }

        @Override
        public ThrowingContext create(ProjectLayout layout) {
            throw new IllegalStateException("model failed");
        }
    }

    public static final class RivalProvider implements ContextProvider<CountedContext> {

        @Override
        public Class<CountedContext> type() {
            return CountedContext.class;
        }

        @Override
        public CountedContext create(ProjectLayout layout) {
            return new CountedContext();
        }
    }

    private ContextResolver resolverWith(Path tempDir, Class<?>... providers) throws IOException {
        Path services = tempDir.resolve("META-INF/services");
        Files.createDirectories(services);
        StringBuilder lines = new StringBuilder();
        for (Class<?> provider : providers) {
            lines.append(provider.getName()).append('\n');
        }
        try (OutputStream out = Files.newOutputStream(services.resolve(ContextProvider.class.getName()))) {
            out.write(lines.toString().getBytes(StandardCharsets.UTF_8));
        }
        ClassLoader loader =
                new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, ContextResolverTest.class.getClassLoader());
        SimpleProjectLayout layout = new SimpleProjectLayout("x", List.of(), List.of(), List.of(), List.of(), loader);
        return new ContextResolver(layout, loader);
    }

    @Test
    void missingProviderIsUnableToVerifyWithHint() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path())) {
            assertThatThrownBy(() -> resolver.get(ArchUnitContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining(
                            "No ContextProvider for dev.quokkify.archgate.context.archunit.ArchUnitContext")
                    .hasMessageContaining("dev.quokkify.archgate:archgate-context-archunit");
        }
    }

    @Test
    void missingProviderForForeignTypeHasNoArtifactHint() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path())) {
            assertThatThrownBy(() -> resolver.get(CustomContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("No ContextProvider for com.example.CustomContext")
                    .hasMessageNotContaining("archgate-context-");
        }
    }

    // CountingProvider increments the static CREATED counter
    @SingleThread
    @Test
    void contextIsCreatedLazilyAndOnce() throws Exception {
        CREATED.set(0);
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path(), CountingProvider.class);
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            assertThat(CREATED).hasValue(0);
            List<Future<CountedContext>> results = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                results.add(executor.submit(() -> resolver.get(CountedContext.class)));
            }
            CountedContext first = results.get(0).get();
            for (Future<CountedContext> result : results) {
                assertThat(result.get()).isSameAs(first);
            }
            assertThat(CREATED).hasValue(1);
        }
    }

    @Test
    void autoCloseableContextIsClosed() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            ContextResolver resolver = resolverWith(tempDir.path(), ClosableProvider.class);
            ClosableContext context = resolver.get(ClosableContext.class);

            resolver.close();

            assertThat(context.closed).isTrue();
        }
    }

    @Test
    void closeFailuresAreSuppressedOnOneError() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            ContextResolver resolver =
                    resolverWith(tempDir.path(), FailingClosableProvider.class, ClosableProvider.class);
            resolver.get(FailingClosableContext.class);
            ClosableContext healthy = resolver.get(ClosableContext.class);

            assertThatThrownBy(resolver::close)
                    .isInstanceOf(UnableToVerifyError.class)
                    .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
            assertThat(healthy.closed).isTrue();
        }
    }

    @Test
    void twoProvidersForOneTypeAreUnableToVerify() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path(), CountingProvider.class, RivalProvider.class)) {
            assertThatThrownBy(() -> resolver.get(CountedContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining(CountingProvider.class.getName())
                    .hasMessageContaining(RivalProvider.class.getName());
        }
    }

    @Test
    void providerFailureIsUnableToVerifyWithCause() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path(), ThrowingProvider.class)) {
            assertThatThrownBy(() -> resolver.get(ThrowingContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining(ThrowingContext.class.getName())
                    .hasRootCauseMessage("model failed");
        }
    }

    // CountingProvider increments the static CREATED counter
    @SingleThread
    @Test
    void buildTimesMergeCreatedContexts() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path(), TimedProvider.class, CountingProvider.class)) {
            assertThat(resolver.buildTimes()).isEmpty();
            resolver.get(TimedContext.class);
            resolver.get(CountedContext.class);

            assertThat(resolver.buildTimes())
                    .containsEntry("main", Duration.ofMillis(7))
                    .containsKeys("TimedContext", "CountedContext")
                    .hasSize(3);
        }
    }

    public static final class CollidingContext implements AnalysisContext {

        @Override
        public Map<String, Duration> buildTimes() {
            return Map.of("CollidingContext", Duration.ofDays(1));
        }
    }

    public static final class CollidingProvider implements ContextProvider<CollidingContext> {

        @Override
        public Class<CollidingContext> type() {
            return CollidingContext.class;
        }

        @Override
        public CollidingContext create(ProjectLayout layout) {
            return new CollidingContext();
        }
    }

    @Test
    void contextOwnBuildTimeWinsOverMeasuredCreateTime() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path(), CollidingProvider.class)) {
            resolver.get(CollidingContext.class);

            assertThat(resolver.buildTimes()).containsExactly(Map.entry("CollidingContext", Duration.ofDays(1)));
        }
    }

    @Test
    void failedCreationIsCachedButEachCallGetsAFreshError() throws IOException {
        try (TempDir tempDir = TempDir.create();
                ContextResolver resolver = resolverWith(tempDir.path())) {
            Throwable first = catchThrowable(() -> resolver.get(ArchUnitContext.class));
            Throwable second = catchThrowable(() -> resolver.get(ArchUnitContext.class));

            assertThat(first).isInstanceOf(UnableToVerifyError.class);
            assertThat(second).isInstanceOf(UnableToVerifyError.class).isNotSameAs(first);
            assertThat(second).hasMessage(first.getMessage());
            assertThat(second.getCause()).isNotNull();
        }
    }
}
