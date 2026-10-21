package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.registry.ValidRule;
import archfixtures.version.ConfiguredVersionedRule;
import archfixtures.version.SecondVersionedRule;
import archfixtures.version.VersionedRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiCompatibilityCheckTest {

    private static final Optional<ApiVersion> RUNTIME = Optional.of(ApiVersion.parse("0.1.0"));

    @TempDir
    Path tempDir;

    private final RuleJars jars = new RuleJars();

    @AfterEach
    void closeLoaders() throws IOException {
        jars.close();
    }

    private List<Class<?>> rulesFrom(String jarName, String apiVersion, Class<?>... fixtures)
            throws IOException, ClassNotFoundException {
        Path jar = RuleJars.write(tempDir.resolve(jarName), apiVersion, List.of(fixtures));
        ClassLoader loader = jars.loader(jar);
        List<Class<?>> loaded = new ArrayList<>();
        for (Class<?> fixture : fixtures) {
            loaded.add(Class.forName(fixture.getName(), false, loader));
        }
        return loaded;
    }

    @Test
    void libraryNeedingNewerApiFails() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "0.2.0", VersionedRule.class);

        assertThatThrownBy(() -> ApiCompatibilityCheck.verify(RUNTIME, rules))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("newer ArchGate API than the one running (0.1.0)")
                .hasMessageContaining("rules-lib-1.0.jar declares Archgate-Api-Version 0.2.0")
                .hasMessageContaining("Raise the archgate plugin version to at least 0.2.0.");
    }

    @Test
    void libraryOnSameApiPasses() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "0.1.0", VersionedRule.class);

        assertThatCode(() -> ApiCompatibilityCheck.verify(RUNTIME, rules)).doesNotThrowAnyException();
    }

    @Test
    void libraryOnOlderApiPasses() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "0.0.9", VersionedRule.class);

        assertThatCode(() -> ApiCompatibilityCheck.verify(RUNTIME, rules)).doesNotThrowAnyException();
    }

    @Test
    void libraryWithoutAttributeIsSkipped() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", null, VersionedRule.class);

        assertThatCode(() -> ApiCompatibilityCheck.verify(RUNTIME, rules)).doesNotThrowAnyException();
    }

    @Test
    void ruleFromDirectoryIsSkipped() {
        assertThatCode(() -> ApiCompatibilityCheck.verify(RUNTIME, List.of(ValidRule.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void unknownRuntimeSkipsTheCheck() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "99.0.0", VersionedRule.class);

        assertThatCode(() -> ApiCompatibilityCheck.verify(Optional.empty(), rules))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidValueFails() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "${archgate.version}", VersionedRule.class);

        assertThatThrownBy(() -> ApiCompatibilityCheck.verify(RUNTIME, rules))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("rules-lib-1.0.jar declares Archgate-Api-Version '${archgate.version}'")
                .hasMessageContaining("expected X.Y.Z or dev-SNAPSHOT");
    }

    @Test
    void devSnapshotRuntimeAcceptsAnyRelease() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "99.0.0", VersionedRule.class);

        assertThatCode(() -> ApiCompatibilityCheck.verify(Optional.of(ApiVersion.parse("dev-SNAPSHOT")), rules))
                .doesNotThrowAnyException();
    }

    @Test
    void releaseRuntimeRejectsDevSnapshotLibrary() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "dev-SNAPSHOT", VersionedRule.class);

        assertThatThrownBy(() -> ApiCompatibilityCheck.verify(RUNTIME, rules))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("rules-lib-1.0.jar declares Archgate-Api-Version dev-SNAPSHOT")
                .hasMessageContaining("Rebuild these libraries against a released archgate-api")
                .hasMessageNotContaining("at least dev-SNAPSHOT");
    }

    @Test
    void subclassInDirectoryOfRuleFromNewerJarFails() throws Exception {
        Path library = RuleJars.write(tempDir.resolve("rules-lib-1.0.jar"), "0.2.0", List.of(VersionedRule.class));
        Path ownClasses = RuleJars.directory(tempDir.resolve("test-classes"), List.of(ConfiguredVersionedRule.class));
        ClassLoader loader = jars.loader(ownClasses, library);
        Class<?> configured = Class.forName(ConfiguredVersionedRule.class.getName(), false, loader);

        assertThatThrownBy(() -> ApiCompatibilityCheck.verify(RUNTIME, List.of(configured)))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("rules-lib-1.0.jar declares Archgate-Api-Version 0.2.0");
    }

    @Test
    void jarWithTwoRulesIsListedOnce() throws Exception {
        List<Class<?>> rules = rulesFrom("rules-lib-1.0.jar", "0.2.0", VersionedRule.class, SecondVersionedRule.class);

        assertThatThrownBy(() -> ApiCompatibilityCheck.verify(RUNTIME, rules))
                .isInstanceOf(UnableToVerifyError.class)
                .satisfies(error -> assertThat(error.getMessage().split("rules-lib-1.0.jar", -1))
                        .hasSize(2));
    }

    @Test
    void everyOffendingJarIsListedAndTheHighestVersionIsRequired() throws Exception {
        List<Class<?>> rules = new ArrayList<>(rulesFrom("a-1.0.jar", "0.3.0", VersionedRule.class));
        rules.addAll(rulesFrom("b-1.0.jar", "0.2.0", SecondVersionedRule.class));

        assertThatThrownBy(() -> ApiCompatibilityCheck.verify(RUNTIME, rules))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("a-1.0.jar declares Archgate-Api-Version 0.3.0")
                .hasMessageContaining("b-1.0.jar declares Archgate-Api-Version 0.2.0")
                .hasMessageContaining("at least 0.3.0.");
    }
}
