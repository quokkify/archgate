package dev.quokkify.archgate.context.classgraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.testng.annotations.Test;

class ClassGraphContextTest {

    private static final String ROOT = "archfixtures.classgraph";
    private static final String SERVICE_PATH = "META-INF/services/archfixtures.classgraph.Greeter";

    private static Path testClasses() throws URISyntaxException {
        return Path.of(ClassGraphContextTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    }

    private static Path validCase() throws URISyntaxException {
        return Path.of(ClassGraphContextTest.class
                .getResource("/classgraph-cases/valid")
                .toURI());
    }

    private static ClassGraphContext context(List<Path> main, List<Path> test) {
        return new ClassGraphContextProvider()
                .create(new SimpleProjectLayout(
                        ROOT, main, test, List.of(), List.of(), ClassGraphContextTest.class.getClassLoader()));
    }

    private static ClassGraphContext context() throws URISyntaxException {
        return context(List.of(testClasses(), validCase()), List.of(testClasses()));
    }

    @Test
    void scanIsCreatedOncePerScope() throws Exception {
        try (ClassGraphContext context = context()) {
            ScanResult first = context.scan(Scope.MAIN);

            assertThat(context.scan(Scope.MAIN)).isSameAs(first);
            assertThat(context.buildTimes()).containsOnlyKeys("classgraph main");
            assertThat(context.scan(Scope.TEST)).isNotSameAs(first);
            assertThat(context.buildTimes()).containsOnlyKeys("classgraph main", "classgraph test");
            assertThat(context.rootPackage()).isEqualTo(ROOT);
            assertThat(context.layout().classDirs(Scope.MAIN)).hasSize(2);
        }
    }

    @Test
    void concurrentCallersShareOneScan() throws Exception {
        try (ClassGraphContext context = context();
                ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<ScanResult>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> context.scan(Scope.MAIN));
            }
            List<ScanResult> results = new ArrayList<>();
            for (Future<ScanResult> future : executor.invokeAll(calls)) {
                results.add(future.get());
            }

            assertThat(results).allSatisfy(result -> assertThat(result).isSameAs(results.get(0)));
        }
    }

    @Test
    void closeClosesScanResults() throws Exception {
        ClassGraphContext context = context();
        ScanResult main = context.scan(Scope.MAIN);
        ScanResult test = context.scan(Scope.TEST);

        context.close();
        context.close();

        assertThatThrownBy(main::getAllClasses).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(test::getAllClasses).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> context.scan(Scope.MAIN)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resourcesUnderServicesAreVisible() throws Exception {
        try (ClassGraphContext context = context()) {
            List<String> paths = context.scan(Scope.MAIN).getAllResources().stream()
                    .map(Resource::getPath)
                    .toList();

            assertThat(paths).contains(SERVICE_PATH);
            assertThat(context.scan(Scope.MAIN).getClassInfo("archfixtures.classgraph.ListedGreeter"))
                    .isNotNull();
        }
    }

    @Test
    void classesOutsideRootPackageAreNotScanned() throws Exception {
        ClassGraphContext other = new ClassGraphContextProvider()
                .create(new SimpleProjectLayout(
                        "archfixtures.nothing",
                        List.of(testClasses()),
                        List.of(),
                        List.of(),
                        List.of(),
                        ClassGraphContextTest.class.getClassLoader()));
        try (other) {
            assertThat(other.scan(Scope.MAIN).getAllStandardClasses()).isEmpty();
        }
    }

    @Test
    void scopeWithoutExistingClassDirectoryIsUnableToVerify() throws Exception {
        try (ClassGraphContext context = context(List.of(testClasses()), List.of(Path.of("missing-dir")))) {
            assertThatThrownBy(() -> context.scan(Scope.TEST))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("No test class directory exists");
        }
    }

    @Test
    void errorFromTheScanIsRethrownUnwrapped() {
        LinkageError failure = new LinkageError("boom");
        ProjectLayout layout = new ProjectLayout() {
            @Override
            public String rootPackage() {
                return ROOT;
            }

            @Override
            public List<Path> classDirs(Scope scope) {
                throw failure;
            }

            @Override
            public List<Path> sourceDirs(Scope scope) {
                return List.of();
            }

            @Override
            public ClassLoader classLoader() {
                return ClassGraphContextTest.class.getClassLoader();
            }
        };
        try (ClassGraphContext context = new ClassGraphContextProvider().create(layout)) {
            assertThatThrownBy(() -> context.scan(Scope.MAIN)).isSameAs(failure);
        }
    }

    @Test
    void providerIsRegisteredAndTyped() {
        List<Class<?>> types = new ArrayList<>();
        ServiceLoader.load(ContextProvider.class).forEach(provider -> types.add(provider.type()));

        assertThat(types).containsExactly(ClassGraphContext.class);
    }
}
