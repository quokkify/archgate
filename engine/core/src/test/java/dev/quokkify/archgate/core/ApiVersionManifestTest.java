package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.registry.ValidRule;
import archfixtures.version.VersionedRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

class ApiVersionManifestTest {

    private static Class<?> loadFrom(RuleJars jars, Path jar) throws IOException, ClassNotFoundException {
        return Class.forName(VersionedRule.class.getName(), false, jars.loader(jar));
    }

    private static Class<?> loadFromJar(TempDir tempDir, RuleJars jars, String apiVersion)
            throws IOException, ClassNotFoundException {
        return loadFrom(
                jars, RuleJars.write(tempDir.resolve("rules-lib-1.0.jar"), apiVersion, List.of(VersionedRule.class)));
    }

    @Test
    void readsAttributeOfTheJarHoldingTheClass() throws Exception {
        try (TempDir tempDir = TempDir.create();
                RuleJars jars = new RuleJars()) {
            assertThat(ApiVersionManifest.of(loadFromJar(tempDir, jars, "0.2.0")))
                    .contains("0.2.0");
        }
    }

    @Test
    void jarWithoutAttributeIsEmpty() throws Exception {
        try (TempDir tempDir = TempDir.create();
                RuleJars jars = new RuleJars()) {
            assertThat(ApiVersionManifest.of(loadFromJar(tempDir, jars, null))).isEmpty();
        }
    }

    @Test
    void jarWithoutManifestIsEmpty() throws Exception {
        try (TempDir tempDir = TempDir.create();
                RuleJars jars = new RuleJars()) {
            Path jar =
                    RuleJars.writeWithoutManifest(tempDir.resolve("rules-lib-1.0.jar"), List.of(VersionedRule.class));

            assertThat(ApiVersionManifest.of(loadFrom(jars, jar))).isEmpty();
        }
    }

    @Test
    void classFromDirectoryHasNoJar() {
        assertThat(ApiVersionManifest.jar(ValidRule.class)).isEmpty();
        assertThat(ApiVersionManifest.of(ValidRule.class)).isEmpty();
    }

    @Test
    void locatesTheJarHoldingTheClass() throws Exception {
        try (TempDir tempDir = TempDir.create();
                RuleJars jars = new RuleJars()) {
            assertThat(ApiVersionManifest.jar(loadFromJar(tempDir, jars, "0.2.0")))
                    .hasValueSatisfying(jar -> assertThat(jar.getFileName()).hasToString("rules-lib-1.0.jar"));
        }
    }

    @Test
    void nonFileLocationHasNoJar() throws Exception {
        Class<?> nested = RuleJars.defineAt(
                VersionedRule.class,
                URI.create("jar:file:/app/boot.jar!/BOOT-INF/lib/rules-lib-1.0.jar")
                        .toURL());

        assertThat(ApiVersionManifest.jar(nested)).isEmpty();
    }

    @Test
    void unreadableJarIsUnableToVerify() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path broken = Files.writeString(tempDir.resolve("broken.jar"), "not a zip");

            assertThatThrownBy(() -> ApiVersionManifest.declared(broken))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("broken.jar");
        }
    }
}
