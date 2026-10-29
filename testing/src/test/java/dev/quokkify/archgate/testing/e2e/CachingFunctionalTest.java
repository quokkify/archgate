package dev.quokkify.archgate.testing.e2e;

import static dev.quokkify.archgate.testing.GradleRun.CHECK_TASK;
import static dev.quokkify.archgate.testing.GradleRun.runner;
import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.annotation.SingleThread;
import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class CachingFunctionalTest {

    private static final String SCENARIO = "pass";
    private static final String CONFIGURATION_CACHE = "--configuration-cache";
    private static final String BUILD_CACHE = "--build-cache";
    private static final String REUSED = "Reusing configuration cache.";
    private static final String PASSED = "Gate PASSED: 2 rules, no findings";
    private static final String OLDER_ARCHUNIT = "com.tngtech.archunit:archunit:1.4.1";
    private static final String FORCED_RESOLUTION = "1.5.1 -> 1.4.1";

    @DataProvider
    static Object[][] scenarios() {
        return new Object[][] {{SCENARIO}, {"multi-module"}};
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void secondRunIsUpToDate() {
        try (TempDir tempDir = TempDir.create()) {
            Path project = materialize(tempDir);
            runner(project, List.of()).build();

            BuildResult second = runner(project, List.of()).build();

            assertThat(second.task(CHECK_TASK).getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        }
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test(dataProvider = "scenarios")
    void configurationCacheIsReused(String scenario) {
        try (TempDir tempDir = TempDir.create()) {
            Path build = Fixture.materialize(scenario, BuildTool.GRADLE, tempDir.resolve(scenario));
            runner(build, List.of(CONFIGURATION_CACHE)).build();

            BuildResult second = runner(build, List.of(CONFIGURATION_CACHE)).build();

            assertThat(second.getOutput()).contains(REUSED);
        }
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void configurationCacheIsReusedWithPropertyOverride() {
        try (TempDir tempDir = TempDir.create()) {
            Path project = materialize(tempDir);
            List<String> args = List.of(CONFIGURATION_CACHE, "-Parchgate.failOn=ERROR");
            BuildResult first = runner(project, args).build();

            BuildResult second = runner(project, args).build();

            assertThat(first.getOutput()).contains("Failure policy (failOn=ERROR):");
            assertThat(second.getOutput()).contains(REUSED);
        }
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void buildCacheHit() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = materialize(tempDir);
            appendTo(
                    project,
                    "settings.gradle",
                    "\nbuildCache {\n    local {\n        directory = file('%s')\n    }\n}\n"
                            .formatted(groovyPath(tempDir.resolve("build-cache"))));
            runner(project, List.of(BUILD_CACHE)).build();
            GradleRunner.create()
                    .withProjectDir(project.toFile())
                    .withPluginClasspath()
                    .withArguments("clean")
                    .build();

            BuildResult second = runner(project, List.of(BUILD_CACHE)).build();

            assertThat(second.task(CHECK_TASK).getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
        }
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void reportFileIsWritten() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = materialize(tempDir);
            runner(project, List.of()).build();

            Path report = project.resolve("build/reports/archgate/report.txt");
            assertThat(Files.readString(report, StandardCharsets.UTF_8)).contains(PASSED);
        }
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void differentArchUnitOnConsumerClasspathStillRuns() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = materialize(tempDir);
            appendTo(project, "build.gradle", """

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
            assertThat(result.task(CHECK_TASK).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
            assertThat(result.getOutput()).contains(PASSED);
        }
    }

    private static Path materialize(TempDir temp) {
        return Fixture.materialize(SCENARIO, BuildTool.GRADLE, temp.resolve("project"));
    }

    private static void appendTo(Path project, String file, String text) throws IOException {
        Files.writeString(project.resolve(file), text, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private static String groovyPath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/");
    }
}
