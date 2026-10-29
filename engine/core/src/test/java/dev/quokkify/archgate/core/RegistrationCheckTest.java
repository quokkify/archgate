package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.registry.NestedHolder;
import archfixtures.registry.ValidRule;
import dev.quokkify.annotation.SingleThread;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import org.testng.SkipException;
import org.testng.annotations.Test;

class RegistrationCheckTest {

    private static final String FIXTURE_PACKAGE_DIR = "archfixtures/registry";
    private static final ClassLoader LOADER = RegistrationCheckTest.class.getClassLoader();

    private static Path copyFixtures(Path tempDir, String... simpleNames) throws IOException {
        Path source = TestDirs.classes().resolve(FIXTURE_PACKAGE_DIR);
        Path target = Files.createDirectories(tempDir.resolve(FIXTURE_PACKAGE_DIR));
        for (String simpleName : simpleNames) {
            Files.copy(source.resolve(simpleName + ".class"), target.resolve(simpleName + ".class"));
        }
        return tempDir;
    }

    @Test
    void unregisteredConcreteRuleFails() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "ValidRule", "UnregisteredRule");
            List<ArchitectureRule<?>> registered = List.of(new ValidRule());

            assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), registered, LOADER))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("archfixtures.registry.UnregisteredRule")
                    .hasMessageContaining(System.lineSeparator() + "  archfixtures.registry.UnregisteredRule")
                    .hasMessageContaining(RuleRegistry.SERVICES_FILE)
                    .hasMessageNotContaining("archfixtures.registry.ValidRule");
        }
    }

    @Test
    void abstractPackagePrivateAndArgConstructorRulesAreIgnored() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "AbstractBaseRule", "PackagePrivateRule", "ArgConstructorRule");

            assertThatCode(() -> RegistrationCheck.verify(List.of(dir), List.of(), LOADER))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void registeredLibraryRulesNeedNotLiveInOwnDirs() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "AbstractBaseRule");
            List<ArchitectureRule<?>> registered = List.of(new ValidRule());

            assertThatCode(() -> RegistrationCheck.verify(List.of(dir), registered, LOADER))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void unregisteredSubclassOfRegisteredRuleFails() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "ValidRule", "SubclassedRule");
            List<ArchitectureRule<?>> registered = List.of(new ValidRule());

            assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), registered, LOADER))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("archfixtures.registry.SubclassedRule")
                    .hasMessageNotContaining("archfixtures.registry.ValidRule");
        }
    }

    @Test
    void moduleInfoAndPackageInfoAreIgnored() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = Files.createDirectories(tempDir.resolve(FIXTURE_PACKAGE_DIR));
            Files.write(tempDir.resolve("module-info.class"), new byte[] {1});
            Files.write(dir.resolve("package-info.class"), new byte[] {1});

            assertThatCode(() -> RegistrationCheck.verify(List.of(tempDir.path()), List.of(), blindLoader()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void classFileTheLoaderCannotSeeIsUnableToVerify() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "UnregisteredRule");

            assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), List.of(), blindLoader()))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("Cannot load archfixtures.registry.UnregisteredRule from " + dir)
                    .hasMessageContaining("test class loader");
        }
    }

    @Test
    void linkageErrorOfAClassIsSkipped() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "ValidRule");
            Files.write(dir.resolve(FIXTURE_PACKAGE_DIR).resolve("Broken.class"), new byte[] {1});

            try (URLClassLoader loader =
                    new URLClassLoader(new URL[] {dir.toUri().toURL()}, blindLoader())) {
                assertThatCode(() -> RegistrationCheck.verify(List.of(dir), List.of(new ValidRule()), loader))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    void missingDirectoryIsSkipped() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "ValidRule");

            assertThatCode(() -> RegistrationCheck.verify(
                            List.of(tempDir.resolve("absent"), dir), List.of(new ValidRule()), LOADER))
                    .doesNotThrowAnyException();
        }
    }

    // strips POSIX permissions from a directory, a process-global file system change
    @SingleThread
    @Test
    void unreadableDirectoryIsUnableToVerify() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "ValidRule");
            Path locked = dir.resolve(FIXTURE_PACKAGE_DIR);
            try {
                Files.setPosixFilePermissions(locked, Set.of());
            } catch (UnsupportedOperationException notPosix) {
                throw new SkipException("POSIX permissions are not supported on this file system");
            }
            try {
                if (Files.isReadable(locked)) {
                    throw new SkipException("directory stays readable (running as root)");
                }
                assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), List.of(), LOADER))
                        .isInstanceOf(UnableToVerifyError.class)
                        .hasMessageContaining(dir.toString());
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        }
    }

    @Test
    void unregisteredPublicStaticNestedRuleFails() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "NestedHolder", "NestedHolder$NestedRule");

            assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), List.of(), LOADER))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("archfixtures.registry.NestedHolder$NestedRule")
                    .hasMessageNotContaining("archfixtures.registry.NestedHolder" + System.lineSeparator());
        }
    }

    @Test
    void registeredPublicStaticNestedRulePasses() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = copyFixtures(tempDir.path(), "NestedHolder", "NestedHolder$NestedRule");

            assertThatCode(() -> RegistrationCheck.verify(List.of(dir), List.of(new NestedHolder.NestedRule()), LOADER))
                    .doesNotThrowAnyException();
        }
    }

    private static ClassLoader blindLoader() {
        return new URLClassLoader(new URL[0], null);
    }
}
