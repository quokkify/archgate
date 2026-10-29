package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.version.ExplodingRule;
import archfixtures.version.VersionedRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.testng.annotations.Test;

class RuleRegistryTest {

    private static List<ArchitectureRule<?>> loadCase(String caseName) throws IOException {
        Path testClasses = TestDirs.classes();
        URL[] urls = {
            testClasses.toUri().toURL(),
            TestDirs.resources().resolve("registry").resolve(caseName).toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader(urls, RuleRegistryTest.class.getClassLoader())) {
            return RuleRegistry.load(loader);
        }
    }

    @Test
    void loadsValidRule() throws IOException {
        List<ArchitectureRule<?>> rules = loadCase("valid");

        assertThat(rules).extracting(ArchitectureRule::name).containsExactly("valid-rule");
    }

    @Test
    void emptyFileIsUnableToVerify() {
        assertThatThrownBy(() -> loadCase("empty"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining(RuleRegistry.SERVICES_FILE)
                .hasMessageContaining("no rules registered");
    }

    @Test
    void missingClassIsUnableToVerify() {
        assertThatThrownBy(() -> loadCase("missing-class"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("archfixtures.registry.DoesNotExistRule");
    }

    @Test
    void missingLibraryClassNamesDependencyHint() {
        assertThatThrownBy(() -> loadCase("missing-library-class"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("dev.quokkify.archgate.rules.archunit.NoThreadSleepRule")
                .hasMessageContaining("Add dev.quokkify.archgate:archgate-rules-archunit as a test dependency.");
    }

    @Test
    void abstractClassIsUnableToVerify() {
        assertThatThrownBy(() -> loadCase("abstract"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("archfixtures.registry.AbstractBaseRule");
    }

    @Test
    void duplicateRegistrationRunsOnce() throws IOException {
        assertThat(loadCase("duplicate")).hasSize(1);
    }

    @Test
    void duplicateRegistrationAcrossTwoServicesFilesRunsOnce() throws IOException {
        Path testClasses = TestDirs.classes();
        Path base = TestDirs.resources().resolve("registry").resolve("duplicate-two-files");
        URL[] urls = {
            testClasses.toUri().toURL(),
            base.resolve("a").toUri().toURL(),
            base.resolve("b").toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader(urls, RuleRegistryTest.class.getClassLoader())) {
            assertThat(RuleRegistry.load(loader)).hasSize(1);
        }
    }

    @Test
    void libraryNeedingNewerApiFailsBeforeAnyRuleIsInstantiated() throws IOException {
        try (TempDir tempDir = TempDir.create();
                RuleJars jars = new RuleJars();
                URLClassLoader loader = jars.loader(
                        RuleJars.write(tempDir.resolve("rules-lib-1.0.jar"), "0.2.0", List.of(ExplodingRule.class)))) {
            assertThatThrownBy(() -> RuleRegistry.load(loader, Optional.of(ApiVersion.parse("0.1.0"))))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("rules-lib-1.0.jar declares Archgate-Api-Version 0.2.0")
                    .hasMessageNotContaining("must not be instantiated");
        }
    }

    @Test
    void compatibleLibraryLoads() throws IOException {
        try (TempDir tempDir = TempDir.create();
                RuleJars jars = new RuleJars();
                URLClassLoader loader = jars.loader(
                        RuleJars.write(tempDir.resolve("rules-lib-1.0.jar"), "0.1.0", List.of(VersionedRule.class)))) {
            assertThat(RuleRegistry.load(loader, Optional.of(ApiVersion.parse("0.1.0"))))
                    .extracting(ArchitectureRule::name)
                    .containsExactly("versioned-rule");
        }
    }
}
