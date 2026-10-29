package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testsupport.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import org.testng.annotations.Test;

class GradleProjectWriterTest {

    @Test
    void gateBuildFileListsModules() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project = Fixture.materialize(
                    "multi-module-violation", BuildTool.GRADLE, work.resolve("p"), work.resolve("repo"), "1.2.3");

            String gate = Files.readString(project.resolve("architecture/build.gradle"));
            assertThat(gate)
                    .contains("id 'dev.quokkify.archgate'")
                    .contains("rootPackage = 'com.example'")
                    .contains("modules(':api', ':tests')")
                    .contains("implementation project(':api')")
                    .contains("'dev.quokkify.archgate:archgate-rules-archunit:1.2.3'");
            assertThat(Files.readString(project.resolve("api/build.gradle")))
                    .contains("id 'dev.quokkify.archgate'")
                    .contains("archgate {\n    skip = true\n}");
            assertThat(Files.readString(project.resolve("tests/build.gradle")))
                    .contains("id 'dev.quokkify.archgate'")
                    .contains("archgate {\n    skip = true\n}");
            assertThat(gate).doesNotContain("skip = true");
            assertThat(Files.readString(project.resolve("settings.gradle")))
                    .contains("rootProject.name = 'multi-module-violation'")
                    .contains("include 'architecture'")
                    .contains(work.resolve("repo").toUri().toString())
                    .contains("mavenCentral()")
                    .doesNotContain("pluginManagement")
                    .doesNotContain("version '1.2.3'");
            assertThat(gate).doesNotContain("version '");
        }
    }

    @Test
    void publishedModePinsPluginVersionFromRepo() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project = Fixture.materialize(
                    "multi-module-violation",
                    BuildTool.GRADLE,
                    work.resolve("p"),
                    work.resolve("repo"),
                    "1.2.3",
                    PluginMode.PUBLISHED);

            assertThat(Files.readString(project.resolve("settings.gradle")))
                    .contains("pluginManagement")
                    .contains("id 'dev.quokkify.archgate' version '1.2.3'")
                    .contains(work.resolve("repo").toUri().toString());
            assertThat(Files.readString(project.resolve("architecture/build.gradle")))
                    .contains("id 'dev.quokkify.archgate'")
                    .contains("'dev.quokkify.archgate:archgate-rules-archunit:1.2.3'");
        }
    }

    @Test
    void ruleLibraryModuleSetsManifestAndArchivesName() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project = Fixture.materialize(
                    "incompatible-rule-library", BuildTool.GRADLE, work.resolve("p"), work.resolve("repo"), "1.2.3");

            String library = Files.readString(project.resolve("rules-lib/build.gradle"));
            assertThat(library)
                    .contains("archivesName = 'rules-lib'")
                    .contains("'Archgate-Api-Version': 'not-a-version'")
                    .doesNotContain("id 'dev.quokkify.archgate'");
            assertThat(Files.readString(project.resolve("architecture/build.gradle")))
                    .contains("testImplementation project(':rules-lib')")
                    .doesNotContain("modules(");
        }
    }

    @Test
    void singleModuleUsesRootBuildFile() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path work = tempDir.path();
            Path project =
                    Fixture.materialize("pass", BuildTool.GRADLE, work.resolve("p"), work.resolve("repo"), "1.2.3");

            assertThat(Files.readString(project.resolve("build.gradle")))
                    .contains("id 'dev.quokkify.archgate'")
                    .contains("rootPackage = 'com.example'")
                    .contains("failOnNoDiscoveredTests = false");
            assertThat(Files.readString(project.resolve("settings.gradle"))).doesNotContain("include ");
        }
    }
}
