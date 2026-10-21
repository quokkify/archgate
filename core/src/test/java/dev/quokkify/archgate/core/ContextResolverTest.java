package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.CustomContext;
import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextResolverTest {

    private static final AtomicInteger CREATED = new AtomicInteger();

    @TempDir
    Path tempDir;

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

    private ContextResolver resolverWith(Class<?>... providers) throws IOException {
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
        try (ContextResolver resolver = resolverWith()) {
            assertThatThrownBy(() -> resolver.get(ArchUnitContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining(
                            "No ContextProvider for dev.quokkify.archgate.context.archunit.ArchUnitContext")
                    .hasMessageContaining("dev.quokkify.archgate:archgate-context-archunit");
        }
    }

    @Test
    void missingProviderForForeignTypeHasNoArtifactHint() throws IOException {
        try (ContextResolver resolver = resolverWith()) {
            assertThatThrownBy(() -> resolver.get(CustomContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("No ContextProvider for com.example.CustomContext")
                    .hasMessageNotContaining("archgate-context-");
        }
    }

    @Test
    void contextIsCreatedLazilyAndOnce() throws Exception {
        CREATED.set(0);
        try (ContextResolver resolver = resolverWith(CountingProvider.class);
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
        ContextResolver resolver = resolverWith(ClosableProvider.class);
        ClosableContext context = resolver.get(ClosableContext.class);

        resolver.close();

        assertThat(context.closed).isTrue();
    }

    @Test
    void closeFailuresAreSuppressedOnOneError() throws IOException {
        ContextResolver resolver = resolverWith(FailingClosableProvider.class, ClosableProvider.class);
        resolver.get(FailingClosableContext.class);
        ClosableContext healthy = resolver.get(ClosableContext.class);

        assertThatThrownBy(resolver::close)
                .isInstanceOf(UnableToVerifyError.class)
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
        assertThat(healthy.closed).isTrue();
    }

    @Test
    void twoProvidersForOneTypeAreUnableToVerify() throws IOException {
        try (ContextResolver resolver = resolverWith(CountingProvider.class, RivalProvider.class)) {
            assertThatThrownBy(() -> resolver.get(CountedContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining(CountingProvider.class.getName())
                    .hasMessageContaining(RivalProvider.class.getName());
        }
    }

    @Test
    void providerFailureIsUnableToVerifyWithCause() throws IOException {
        try (ContextResolver resolver = resolverWith(ThrowingProvider.class)) {
            assertThatThrownBy(() -> resolver.get(ThrowingContext.class))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining(ThrowingContext.class.getName())
                    .hasRootCauseMessage("model failed");
        }
    }

    @Test
    void buildTimesMergeCreatedContexts() throws IOException {
        try (ContextResolver resolver = resolverWith(TimedProvider.class, CountingProvider.class)) {
            assertThat(resolver.buildTimes()).isEmpty();
            resolver.get(TimedContext.class);
            resolver.get(CountedContext.class);

            assertThat(resolver.buildTimes())
                    .containsEntry("main", Duration.ofMillis(7))
                    .hasSize(1);
        }
    }
}
