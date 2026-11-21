package dev.quokkify.archgate.testing.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class JvmPluginRequiredFunctionalTest {

    private static final String NO_JVM_PLUGIN =
            "archgate: project ':' applies dev.quokkify.archgate" + " but not the java plugin";
    private static final String BASE_PROJECT = """
            plugins {
                id 'base'
                id 'dev.quokkify.archgate'
            }

            archgate {
            %s}
            """;

    /** Regression guard: without the java plugin the plugin registered no task and check passed silently. */
    @Test(dataProvider = "cacheModes")
    void projectWithoutJavaPluginFails(List<String> extraArgs) throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = project(tempDir.path(), "    rootPackage = 'com.example'\n", "");

            BuildResult result = runner(project, extraArgs).buildAndFail();

            assertThat(result.getOutput())
                    .contains(NO_JVM_PLUGIN)
                    .contains("archgate { skip = true }")
                    .contains("Android projects are not supported");
        }
    }

    @Test
    void aggregatorWithoutJavaPluginNamesTheGateProject() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = project(
                    tempDir.path(), "    rootPackage = 'com.example'\n    modules(':api')\n", "include 'api'\n");
            write(project.resolve("api/build.gradle"), """
                    plugins {
                        id 'java'
                        id 'dev.quokkify.archgate'
                    }

                    archgate {
                        skip = true
                    }
                    """);

            BuildResult result = runner(project, List.of()).buildAndFail();

            assertThat(result.getOutput()).contains(NO_JVM_PLUGIN).contains("modules(...)");
        }
    }

    @Test(dataProvider = "skipSettings")
    void skippedProjectWithoutJavaPluginPasses(String block, List<String> extraArgs) throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = project(tempDir.path(), block, "");

            BuildResult result = runner(project, extraArgs).build();

            assertThat(result.getOutput()).doesNotContain(NO_JVM_PLUGIN);
        }
    }

    @DataProvider
    static Object[][] cacheModes() {
        return new Object[][] {{List.of()}, {List.of("--configuration-cache")}};
    }

    @DataProvider
    static Object[][] skipSettings() {
        return new Object[][] {
            {"    skip = true\n", List.of()},
            {"    rootPackage = 'com.example'\n", List.of("-Parchgate.skip=true")},
            {"    skip = true\n", List.of("--configuration-cache")}
        };
    }

    private static Path project(Path temp, String archgateBlock, String includes) throws IOException {
        Path project = temp.resolve("project");
        write(project.resolve("settings.gradle"), "rootProject.name = 'aggregate'\n" + includes);
        write(project.resolve("build.gradle"), BASE_PROJECT.formatted(archgateBlock));
        return project;
    }

    private static GradleRunner runner(Path project, List<String> extraArgs) {
        List<String> arguments = new ArrayList<>(List.of("check", "--stacktrace"));
        arguments.addAll(extraArgs);
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
