package dev.quokkify.archgate.testing.e2e;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.testkit.runner.BuildResult;
import org.testng.annotations.Test;

/**
 * Runs an external rule whose variable is missing under Gradle and checks where the CI decision comes from.
 *
 * <p>Each build gets an environment copied from the test process without {@code CI}, the variable and any
 * {@code archgate.ci} project property, so the result does not depend on the machine that runs the suite.
 */
class CiSwitchesFunctionalTest {

    private static final String SCENARIO = "pass";
    private static final String CI_VARIABLE = "CI";
    private static final String TOKEN = "TRACKER_TOKEN";
    private static final List<String> UNPINNED =
            List.of(CI_VARIABLE, TOKEN, "archgate.ci", "ORG_GRADLE_PROJECT_archgate.ci");
    private static final String RULE_SERVICES =
            "src/test/resources/META-INF/services/dev.quokkify.archgate.api.ArchitectureRule";
    private static final String REQUIRING_RULE = """
            package com.example;

            import dev.quokkify.archgate.api.ArchitectureRule;
            import dev.quokkify.archgate.api.Severity;
            import dev.quokkify.archgate.context.archunit.ArchUnitContext;

            public class TrackerTokenRule implements ArchitectureRule<ArchUnitContext> {

                @Override
                public String name() {
                    return "Tracker token";
                }

                @Override
                public Severity severity() {
                    return Severity.ERROR;
                }

                @Override
                public Class<ArchUnitContext> contextType() {
                    return ArchUnitContext.class;
                }

                @Override
                public void verify(ArchUnitContext context) {
                    context.environment().require("TRACKER_TOKEN");
                }
            }
            """;

    /** Regression guard: {@code -Parchgate.ci=true} was ignored, so a CI run skipped the rule and passed. */
    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void projectPropertySwitchesCiOn() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = projectWithRequiringRule(tempDir);

            BuildResult result = runner(project, List.of("-Parchgate.ci=true"))
                    .withEnvironment(environment(Map.of()))
                    .buildAndFail();

            assertThat(result.getOutput()).contains("Rule 'Tracker token' cannot run in CI (archgate.ci=true)");
        }
    }

    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void projectPropertyWinsOverSystemProperty() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = projectWithRequiringRule(tempDir);

            BuildResult result = runner(project, List.of("-Parchgate.ci=true", "-Darchgate.ci=false"))
                    .withEnvironment(environment(Map.of()))
                    .buildAndFail();

            assertThat(result.getOutput()).contains("cannot run in CI (archgate.ci=true)");
        }
    }

    /** The {@code CI} variable of the client reaches the worker in the daemon when nothing pins archgate.ci. */
    // shares the TestKit build and configuration caches
    @SingleThread
    @Test
    void ciVariableReachesTheWorker() throws IOException {
        try (TempDir tempDir = TempDir.create()) {
            Path project = projectWithRequiringRule(tempDir);

            BuildResult result = runner(project, List.of())
                    .withEnvironment(environment(Map.of(CI_VARIABLE, "true")))
                    .buildAndFail();

            assertThat(result.getOutput()).contains("Rule 'Tracker token' cannot run in CI (CI is set)");
        }
    }

    private static Map<String, String> environment(Map<String, String> extra) {
        Map<String, String> environment = new HashMap<>(System.getenv());
        UNPINNED.forEach(environment::remove);
        environment.putAll(extra);
        return environment;
    }

    private static Path projectWithRequiringRule(TempDir temp) throws IOException {
        Path project = Fixture.materialize(SCENARIO, BuildTool.GRADLE, temp.resolve("project"));
        Path rule = project.resolve("src/test/java/com/example/TrackerTokenRule.java");
        Files.createDirectories(rule.getParent());
        Files.writeString(rule, REQUIRING_RULE, StandardCharsets.UTF_8);
        Files.writeString(
                project.resolve(RULE_SERVICES),
                "com.example.TrackerTokenRule\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        return project;
    }
}
