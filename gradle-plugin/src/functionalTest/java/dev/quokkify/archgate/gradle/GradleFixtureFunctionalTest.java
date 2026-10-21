package dev.quokkify.archgate.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GradleFixtureFunctionalTest {

    static final String IT_REPO = "archgate.itRepo";
    static final String VERSION = "archgate.version";
    static final String TASK = ":archgateCheck";

    @TempDir
    Path temp;

    static Stream<String> scenarios() {
        return Fixture.scenarios().stream();
    }

    static Path materialize(String scenario, Path target) {
        return Fixture.materialize(
                scenario, BuildTool.GRADLE, target, Path.of(System.getProperty(IT_REPO)), System.getProperty(VERSION));
    }

    static GradleRunner runner(Path project, List<String> args) {
        List<String> arguments = new ArrayList<>(List.of("check", "--stacktrace"));
        arguments.addAll(args);
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void runsScenario(String scenario) {
        Path project = materialize(scenario, temp.resolve("project"));
        for (Expectation expectation : Expectation.load(Fixture.root().resolve(scenario))) {
            run(project, expectation);
        }
    }

    private static void run(Path project, Expectation expectation) {
        GradleRunner runner = runner(project, expectation.args(BuildTool.GRADLE));
        BuildResult result = expectation.success() ? runner.build() : runner.buildAndFail();
        String output = result.getOutput();
        for (String text : expectation.contains(BuildTool.GRADLE)) {
            assertThat(output).as("run %d", expectation.run()).contains(text);
        }
        for (String text : expectation.absent(BuildTool.GRADLE)) {
            assertThat(output).as("run %d", expectation.run()).doesNotContain(text);
        }
        for (Pattern pattern : expectation.matches(BuildTool.GRADLE)) {
            assertThat(pattern.matcher(output).find())
                    .as("run %d matches %s", expectation.run(), pattern)
                    .isTrue();
        }
        if (expectation.skipped()) {
            BuildTask task = result.task(TASK);
            assertThat(task).as("run %d task %s", expectation.run(), TASK).isNotNull();
            assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        }
    }
}
