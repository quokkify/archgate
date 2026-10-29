package dev.quokkify.archgate.context.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

class ArchUnitContextTest {

    private static final String ROOT = "archfixtures.context";

    @Test
    void mainClassesImportsOnlyRootPackage() throws IOException {
        Path mainDir = dirWith("archfixtures/context/MainFoo", "archfixtures/other/Outside");
        ArchUnitContext context = context(ROOT, List.of(mainDir), List.of());

        assertThat(names(context.main())).containsExactly("MainFoo");
    }

    @Test
    void missingTestDirIsSkipped() throws IOException {
        Path testDir = dirWith("archfixtures/context/TestFoo");
        Path missing = testDir.resolveSibling("does-not-exist-" + System.nanoTime());
        ArchUnitContext context = context(ROOT, List.of(), List.of(missing, testDir));

        assertThat(names(context.test())).containsExactly("TestFoo");
    }

    @Test
    void emptyViewIsUnableToVerify() throws IOException {
        Path missing = Files.createTempDirectory("archgate").resolve("does-not-exist");
        ArchUnitContext context = context(ROOT, List.of(), List.of(missing));

        assertThatThrownBy(context::test)
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining(ROOT);
    }

    @Test
    void constructorDoesNotImport() throws IOException {
        Path missing = Files.createTempDirectory("archgate").resolve("does-not-exist");

        assertThatCode(() -> context(ROOT, List.of(missing), List.of(missing))).doesNotThrowAnyException();
    }

    @Test
    void allIsUnionOfMainAndTest() throws IOException {
        Path mainDir = dirWith("archfixtures/context/MainFoo", "archfixtures/other/Outside");
        Path testDir = dirWith("archfixtures/context/TestFoo");
        ArchUnitContext context = context(ROOT, List.of(mainDir), List.of(testDir));

        assertThat(names(context.all())).containsExactlyInAnyOrder("MainFoo", "TestFoo");
    }

    @Test
    void classDirsReturnsConfiguredDirs() throws IOException {
        Path mainDir = dirWith("archfixtures/context/MainFoo");
        Path testDir = dirWith("archfixtures/context/TestFoo");
        ArchUnitContext context = context(ROOT, List.of(mainDir), List.of(testDir));

        assertThat(context.rootPackage()).isEqualTo(ROOT);
        assertThat(context.layout().classDirs(Scope.MAIN)).containsExactly(mainDir);
        assertThat(context.layout().classDirs(Scope.TEST)).containsExactly(testDir);
    }

    @Test
    void modelsAreBuiltOnce() throws IOException {
        Path mainDir = dirWith("archfixtures/context/MainFoo");
        ArchUnitContext context = context(ROOT, List.of(mainDir), List.of());

        assertThat(context.main()).isSameAs(context.main());
        assertThat(context.buildTimes()).containsOnlyKeys("main");
        context.all();
        assertThat(context.buildTimes()).containsOnlyKeys("main", "all");
    }

    private static ArchUnitContext context(String root, List<Path> mainDirs, List<Path> testDirs) {
        return ArchUnitContexts.of(new TestLayout(root, mainDirs, testDirs));
    }

    private record TestLayout(String rootPackage, List<Path> mainDirs, List<Path> testDirs) implements ProjectLayout {

        @Override
        public List<Path> classDirs(Scope scope) {
            return scope == Scope.MAIN ? mainDirs : testDirs;
        }

        @Override
        public List<Path> sourceDirs(Scope scope) {
            return List.of();
        }

        @Override
        public ClassLoader classLoader() {
            return ArchUnitContextTest.class.getClassLoader();
        }
    }

    private static List<String> names(JavaClasses classes) {
        return classes.stream().map(JavaClass::getSimpleName).toList();
    }

    private static Path dirWith(String... classResources) throws IOException {
        Path source = Path.of(ArchUnitContextTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath());
        Path target = Files.createTempDirectory("archgate-classes");
        for (String resource : classResources) {
            Path from = source.resolve(resource + ".class");
            Path to = target.resolve(resource + ".class");
            Files.createDirectories(to.getParent());
            Files.copy(from, to);
        }
        return target;
    }

    @Test
    void providerCreatesArchUnitContext() throws IOException {
        Path mainDir = dirWith("archfixtures/context/MainFoo");
        ArchUnitContextProvider provider = new ArchUnitContextProvider();

        assertThat(provider.type()).isEqualTo(ArchUnitContext.class);
        assertThat(names(provider.create(new TestLayout(ROOT, List.of(mainDir), List.of()))
                        .main()))
                .containsExactly("MainFoo");
    }

    @Test
    void providerIsRegisteredAsService() {
        assertThat(java.util.ServiceLoader.load(dev.quokkify.archgate.api.ContextProvider.class))
                .anyMatch(ArchUnitContextProvider.class::isInstance);
    }
}
