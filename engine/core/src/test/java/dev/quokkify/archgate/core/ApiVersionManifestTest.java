package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.registry.ValidRule;
import archfixtures.version.VersionedRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiVersionManifestTest {

    @TempDir
    Path tempDir;

    private final RuleJars jars = new RuleJars();

    @AfterEach
    void closeLoaders() throws IOException {
        jars.close();
    }

    private Class<?> loadFrom(Path jar) throws IOException, ClassNotFoundException {
        return Class.forName(VersionedRule.class.getName(), false, jars.loader(jar));
    }

    private Class<?> loadFromJar(String apiVersion) throws IOException, ClassNotFoundException {
        return loadFrom(RuleJars.write(tempDir.resolve("rules-lib-1.0.jar"), apiVersion, List.of(VersionedRule.class)));
    }

    @Test
    void readsAttributeOfTheJarHoldingTheClass() throws Exception {
        assertThat(ApiVersionManifest.of(loadFromJar("0.2.0"))).contains("0.2.0");
    }

    @Test
    void jarWithoutAttributeIsEmpty() throws Exception {
        assertThat(ApiVersionManifest.of(loadFromJar(null))).isEmpty();
    }

    @Test
    void jarWithoutManifestIsEmpty() throws Exception {
        Path jar = RuleJars.writeWithoutManifest(tempDir.resolve("rules-lib-1.0.jar"), List.of(VersionedRule.class));

        assertThat(ApiVersionManifest.of(loadFrom(jar))).isEmpty();
    }

    @Test
    void classFromDirectoryHasNoJar() {
        assertThat(ApiVersionManifest.jar(ValidRule.class)).isEmpty();
        assertThat(ApiVersionManifest.of(ValidRule.class)).isEmpty();
    }

    @Test
    void locatesTheJarHoldingTheClass() throws Exception {
        assertThat(ApiVersionManifest.jar(loadFromJar("0.2.0")))
                .hasValueSatisfying(jar -> assertThat(jar.getFileName()).hasToString("rules-lib-1.0.jar"));
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
        Path broken = Files.writeString(tempDir.resolve("broken.jar"), "not a zip");

        assertThatThrownBy(() -> ApiVersionManifest.declared(broken))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("broken.jar");
    }
}
