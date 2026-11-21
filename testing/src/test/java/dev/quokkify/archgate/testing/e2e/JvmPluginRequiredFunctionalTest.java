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
    private static final String UNCONFIGURED_BOM = "archgate: :bom has no JVM plugin and no archgate configuration";
    private static final String BLANKET_ROOT = """
            plugins {
                id 'dev.quokkify.archgate' apply false
            }

            subprojects {
            %s}
            """;
    private static final String APPLY_EVERYWHERE = "    apply plugin: 'dev.quokkify.archgate'\n";
    private static final String APPLY_WITH_JAVA =
            "    plugins.withId('java') {\n        apply plugin: 'dev.quokkify.archgate'\n    }\n";
    private static final String APP_PROJECT = """
            plugins {
                id 'java'
            }

            archgate {
                rootPackage = 'com.example'
            }
            """;
    private static final String PLATFORM_PROJECT = """
            plugins {
                id 'java-platform'
            }
            %s""";
    private static final String APP_GATE = ":app:archgateCheck SKIPPED";
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
                    .contains("-Parchgate.skip=true skips the gate in every project")
                    .contains("plugins.withId('java')")
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

    @Test(dataProvider = "unconfiguredPlatformArgs")
    void blanketAppliedPlatformWithoutConfigurationIsLeftAlone(List<String> extraArgs) throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = mixedBuild(tempDir.path(), APPLY_EVERYWHERE, "");

            BuildResult result = dryRun(project, extraArgs).build();

            assertThat(result.getOutput())
                    .contains(UNCONFIGURED_BOM)
                    .contains(APP_GATE)
                    .doesNotContain(":bom:archgateCheck");
        }
    }

    @Test
    void blanketAppliedPlatformWithConfigurationFails() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project =
                    mixedBuild(tempDir.path(), APPLY_EVERYWHERE, "\narchgate {\n    rootPackage = 'com.example'\n}\n");

            BuildResult result = dryRun(project, List.of()).buildAndFail();

            assertThat(result.getOutput())
                    .contains("archgate: project ':bom' applies dev.quokkify.archgate but not the java plugin");
        }
    }

    @Test
    void pluginAppliedWithJavaOnlyGatesJavaProjects() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = mixedBuild(tempDir.path(), APPLY_WITH_JAVA, "");

            BuildResult result = dryRun(project, List.of()).build();

            assertThat(result.getOutput()).contains(APP_GATE).doesNotContain("archgate:");
        }
    }

    @DataProvider
    static Object[][] unconfiguredPlatformArgs() {
        return new Object[][] {
            {List.of()}, {List.of("--configuration-cache")}, {List.of("-Parchgate.rootPackage=com.example")}
        };
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

    private static Path mixedBuild(Path temp, String applyBlock, String platformArchgate) throws IOException {
        Path project = temp.resolve("project");
        write(project.resolve("settings.gradle"), "rootProject.name = 'mixed'\ninclude 'app', 'bom'\n");
        write(project.resolve("build.gradle"), BLANKET_ROOT.formatted(applyBlock));
        write(project.resolve("app/build.gradle"), APP_PROJECT);
        write(project.resolve("bom/build.gradle"), PLATFORM_PROJECT.formatted(platformArchgate));
        return project;
    }

    private static GradleRunner dryRun(Path project, List<String> extraArgs) {
        List<String> arguments = new ArrayList<>(List.of("--dry-run"));
        arguments.addAll(extraArgs);
        return runner(project, arguments);
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
