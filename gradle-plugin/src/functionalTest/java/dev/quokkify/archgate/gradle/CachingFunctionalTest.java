package dev.quokkify.archgate.gradle;

import static dev.quokkify.archgate.gradle.GradleFixtureFunctionalTest.TASK;
import static dev.quokkify.archgate.gradle.GradleFixtureFunctionalTest.materialize;
import static dev.quokkify.archgate.gradle.GradleFixtureFunctionalTest.runner;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CachingFunctionalTest {

    private static final String SCENARIO = "pass";
    private static final String CONFIGURATION_CACHE = "--configuration-cache";
    private static final String BUILD_CACHE = "--build-cache";
    private static final String REUSED = "Reusing configuration cache.";
    private static final String PASSED = "Gate PASSED: 2 rules, no findings";
    private static final String OLDER_ARCHUNIT = "com.tngtech.archunit:archunit:1.4.1";
    private static final String FORCED_RESOLUTION = "1.5.1 -> 1.4.1";

    @TempDir
    Path temp;

    private Path project;

    @BeforeEach
    void materializeFixture() {
        project = materialize(SCENARIO, temp.resolve("project"));
    }

    @Test
    void secondRunIsUpToDate() {
        runner(project, List.of()).build();

        BuildResult second = runner(project, List.of()).build();

        assertThat(second.task(TASK).getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {SCENARIO, "multi-module"})
    void configurationCacheIsReused(String scenario) {
        Path build = materialize(scenario, temp.resolve(scenario));
        runner(build, List.of(CONFIGURATION_CACHE)).build();

        BuildResult second = runner(build, List.of(CONFIGURATION_CACHE)).build();

        assertThat(second.getOutput()).contains(REUSED);
    }

    @Test
    void configurationCacheIsReusedWithPropertyOverride() {
        List<String> args = List.of(CONFIGURATION_CACHE, "-Parchgate.failOn=ERROR");
        BuildResult first = runner(project, args).build();

        BuildResult second = runner(project, args).build();

        assertThat(first.getOutput()).contains("Failure policy (failOn=ERROR):");
        assertThat(second.getOutput()).contains(REUSED);
    }

    @Test
    void buildCacheHit() throws IOException {
        appendTo(
                "settings.gradle",
                "\nbuildCache {\n    local {\n        directory = file('%s')\n    }\n}\n"
                        .formatted(groovyPath(temp.resolve("build-cache"))));
        runner(project, List.of(BUILD_CACHE)).build();
        GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments("clean")
                .build();

        BuildResult second = runner(project, List.of(BUILD_CACHE)).build();

        assertThat(second.task(TASK).getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
    }

    @Test
    void reportFileIsWritten() throws IOException {
        runner(project, List.of()).build();

        Path report = project.resolve("build/reports/archgate/report.txt");
        assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains(PASSED);
    }

    @Test
    void differentArchUnitOnConsumerClasspathStillRuns() throws IOException {
        appendTo("build.gradle", """

                dependencies {
                    testImplementation '%1$s'
                }

                configurations.configureEach {
                    resolutionStrategy.force '%1$s'
                }
                """.formatted(OLDER_ARCHUNIT));

        BuildResult insight = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(
                        "dependencyInsight",
                        "--configuration",
                        "testRuntimeClasspath",
                        "--dependency",
                        "com.tngtech.archunit:archunit")
                .build();
        BuildResult result = runner(project, List.of()).build();

        assertThat(insight.getOutput()).contains(FORCED_RESOLUTION);
        assertThat(result.task(TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains(PASSED);
    }

    private void appendTo(String file, String text) throws IOException {
        Files.writeString(project.resolve(file), text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String groovyPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/");
    }
}
