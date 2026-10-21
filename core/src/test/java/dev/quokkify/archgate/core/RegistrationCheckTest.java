package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.registry.ValidRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistrationCheckTest {

    private static final String FIXTURE_PACKAGE_DIR = "archfixtures/registry";
    private static final ClassLoader LOADER = RegistrationCheckTest.class.getClassLoader();

    @TempDir
    Path tempDir;

    private Path copyFixtures(String... simpleNames) throws IOException {
        Path source = TestDirs.classes().resolve(FIXTURE_PACKAGE_DIR);
        Path target = Files.createDirectories(tempDir.resolve(FIXTURE_PACKAGE_DIR));
        for (String simpleName : simpleNames) {
            Files.copy(source.resolve(simpleName + ".class"), target.resolve(simpleName + ".class"));
        }
        return tempDir;
    }

    @Test
    void unregisteredConcreteRuleFails() throws IOException {
        Path dir = copyFixtures("ValidRule", "UnregisteredRule");
        List<ArchitectureRule<?>> registered = List.of(new ValidRule());

        assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), registered, LOADER))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("archfixtures.registry.UnregisteredRule")
                .hasMessageContaining(System.lineSeparator() + "  archfixtures.registry.UnregisteredRule")
                .hasMessageContaining(RuleRegistry.SERVICES_FILE)
                .hasMessageNotContaining("archfixtures.registry.ValidRule");
    }

    @Test
    void abstractPackagePrivateAndArgConstructorRulesAreIgnored() throws IOException {
        Path dir = copyFixtures("AbstractBaseRule", "PackagePrivateRule", "ArgConstructorRule");

        assertThatCode(() -> RegistrationCheck.verify(List.of(dir), List.of(), LOADER))
                .doesNotThrowAnyException();
    }

    @Test
    void registeredLibraryRulesNeedNotLiveInOwnDirs() throws IOException {
        Path dir = copyFixtures("AbstractBaseRule");
        List<ArchitectureRule<?>> registered = List.of(new ValidRule());

        assertThatCode(() -> RegistrationCheck.verify(List.of(dir), registered, LOADER))
                .doesNotThrowAnyException();
    }

    @Test
    void unregisteredSubclassOfRegisteredRuleFails() throws IOException {
        Path dir = copyFixtures("ValidRule", "SubclassedRule");
        List<ArchitectureRule<?>> registered = List.of(new ValidRule());

        assertThatThrownBy(() -> RegistrationCheck.verify(List.of(dir), registered, LOADER))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("archfixtures.registry.SubclassedRule")
                .hasMessageNotContaining("archfixtures.registry.ValidRule");
    }

    @Test
    void moduleInfoAndPackageInfoAreIgnored() throws IOException {
        Path dir = copyFixtures("ValidRule");
        Files.write(tempDir.resolve("module-info.class"), new byte[] {1});
        Files.write(dir.resolve(FIXTURE_PACKAGE_DIR).resolve("package-info.class"), new byte[] {1});

        assertThatCode(() -> RegistrationCheck.verify(List.of(dir), List.of(new ValidRule()), LOADER))
                .doesNotThrowAnyException();
    }

    @Test
    void missingDirectoryAndUnloadableClassAreSkipped() throws IOException {
        Path dir = copyFixtures("ValidRule");
        Files.write(tempDir.resolve("Broken.class"), new byte[] {1});

        assertThatCode(() -> RegistrationCheck.verify(
                        List.of(tempDir.resolve("absent"), dir), List.of(new ValidRule()), LOADER))
                .doesNotThrowAnyException();
    }
}
