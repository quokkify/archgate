package dev.quokkify.archgate.testing.e2e;

import static dev.quokkify.archgate.testing.BuildTool.GRADLE;
import static dev.quokkify.archgate.testing.BuildTool.MAVEN;
import static dev.quokkify.archgate.testing.GradleRun.CHECK_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testing.FixtureSpec;
import dev.quokkify.archgate.testing.FixtureTools;
import dev.quokkify.archgate.testing.GradleRun;
import dev.quokkify.archgate.testing.MavenCache;
import dev.quokkify.archgate.testing.MavenRun;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.shared.verifier.VerificationException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FixtureEndToEndTest {

    private static final Set<String> NOT_COMPARED = Set.of("skip");
    private static final Path MAVEN_REPORT = Path.of("target", "archgate", "report.txt");
    private static final Path GRADLE_REPORT = Path.of("build", "reports", "archgate", "report.txt");

    @TempDir
    Path temp;

    static Stream<String> scenarios() {
        return Fixture.scenarios().stream();
    }

    static Stream<String> comparedScenarios() {
        return scenarios().filter(scenario -> !NOT_COMPARED.contains(scenario));
    }

    @Test
    void comparesEveryScenarioButSkip() {
        assertThat(comparedScenarios())
                .doesNotContain("skip")
                .contains("javaparser-wildcard", "service-registration", "missing-services");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void runsScenario(String scenario) {
        Set<BuildTool> tools = FixtureTools.fromSystemProperty();
        boolean compared = tools.size() == BuildTool.values().length && !NOT_COMPARED.contains(scenario);
        List<Expectation> expectations = Expectation.load(Fixture.root().resolve(scenario));
        if (compared) {
            assertThat(expectations)
                    .as("scenario %s needs a run with contains entries to prove the result", scenario)
                    .anyMatch(expectation -> !expectation.contains(MAVEN).isEmpty());
        }
        String gate = FixtureSpec.load(Fixture.root().resolve(scenario)).gate().orElse("");
        Map<BuildTool, Path> projects = new EnumMap<>(BuildTool.class);
        for (BuildTool tool : tools) {
            projects.put(tool, Fixture.materialize(scenario, tool, temp.resolve(tool.name())));
        }
        for (Expectation expectation : expectations) {
            Map<BuildTool, BuildResultView> results = new EnumMap<>(BuildTool.class);
            if (tools.contains(MAVEN)) {
                results.put(MAVEN, runMaven(projects.get(MAVEN), gate, expectation));
            }
            if (tools.contains(GRADLE)) {
                results.put(GRADLE, runGradle(projects.get(GRADLE), gate, expectation));
            }
            if (compared) {
                assertSameResult(scenario, expectation, results.get(MAVEN), results.get(GRADLE));
            }
        }
    }

    private BuildResultView runMaven(Path project, String gate, Expectation expectation) {
        Path localRepo = temp.resolve("m2");
        MavenCache cache = MavenCache.fromSystemProperty();
        MavenRun.Outcome outcome;
        try {
            outcome = MavenRun.execute(project, localRepo, cache, expectation);
        } catch (VerificationException | IOException e) {
            throw new IllegalStateException("Maven run " + expectation.run() + " could not be executed", e);
        } finally {
            cache.promote(localRepo);
        }
        boolean succeeded = outcome.succeeded();
        String log = outcome.log();
        assertThat(succeeded)
                .as("maven run %d succeeded%n%s", expectation.run(), log)
                .isEqualTo(expectation.success());
        for (String text : expectation.contains(MAVEN)) {
            assertThat(log).as("maven run %d", expectation.run()).contains(text);
        }
        for (String text : expectation.absent(MAVEN)) {
            assertThat(log).as("maven run %d", expectation.run()).doesNotContain(text);
        }
        for (Pattern pattern : expectation.matches(MAVEN)) {
            assertThat(pattern.matcher(log).find())
                    .as("maven run %d matches %s%n%s", expectation.run(), pattern, log)
                    .isTrue();
        }
        Path report = project.resolve(gate).resolve(MAVEN_REPORT);
        return new BuildResultView(succeeded, log, readReport(report), report);
    }

    private static BuildResultView runGradle(Path project, String gate, Expectation expectation) {
        GradleRunner runner = GradleRun.runner(project, expectation.args(GRADLE));
        BuildResult result = expectation.success() ? runner.build() : runner.buildAndFail();
        String output = result.getOutput();
        for (String text : expectation.contains(GRADLE)) {
            assertThat(output).as("gradle run %d", expectation.run()).contains(text);
        }
        for (String text : expectation.absent(GRADLE)) {
            assertThat(output).as("gradle run %d", expectation.run()).doesNotContain(text);
        }
        for (Pattern pattern : expectation.matches(GRADLE)) {
            assertThat(pattern.matcher(output).find())
                    .as("gradle run %d matches %s", expectation.run(), pattern)
                    .isTrue();
        }
        if (expectation.skipped()) {
            BuildTask task = result.task(CHECK_TASK);
            assertThat(task)
                    .as("gradle run %d task %s", expectation.run(), CHECK_TASK)
                    .isNotNull();
            assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SKIPPED);
        }
        Path report = project.resolve(gate).resolve(GRADLE_REPORT);
        return new BuildResultView(expectation.success(), output, readReport(report), report);
    }

    private static void assertSameResult(
            String scenario, Expectation expectation, BuildResultView m, BuildResultView g) {
        assertThat(m.success()).as("maven success, run %d", expectation.run()).isEqualTo(expectation.success());
        assertThat(g.success()).as("gradle success, run %d", expectation.run()).isEqualTo(m.success());
        if (expectation.unableToVerify()) {
            assertSameMessage(scenario, expectation, m, g);
        } else {
            assertSameReport(expectation, m, g);
        }
    }

    private static void assertSameReport(Expectation expectation, BuildResultView m, BuildResultView g) {
        assertThat(m.reportPath()).as("Maven report %s", m.reportPath()).isRegularFile();
        assertThat(g.reportPath()).as("Gradle report %s", g.reportPath()).isRegularFile();
        String[] expected = expectation.contains(MAVEN).toArray(String[]::new);
        if (expected.length > 0) {
            assertThat(m.report()).as("Maven report %s", m.reportPath()).contains(expected);
            assertThat(g.report()).as("Gradle report %s", g.reportPath()).contains(expected);
        }
        assertThat(ReportNormalizer.normalize(g.report()))
                .as("report of run %d", expectation.run())
                .isEqualTo(ReportNormalizer.normalize(m.report()));
    }

    private static void assertSameMessage(
            String scenario, Expectation expectation, BuildResultView m, BuildResultView g) {
        assertThat(m.reportPath()).as("Maven report %s", m.reportPath()).doesNotExist();
        assertThat(g.reportPath()).as("Gradle report %s", g.reportPath()).doesNotExist();
        assertThat(expectation.contains(MAVEN))
                .as("scenario %s needs a contains entry to locate the message", scenario)
                .isNotEmpty();
        String needle = expectation.contains(MAVEN).get(0);
        assertThat(ReportNormalizer.messageLine(g.log(), needle))
                .as("message line containing '%s'", needle)
                .isEqualTo(ReportNormalizer.messageLine(m.log(), needle));
    }

    private static String readReport(Path report) {
        if (!Files.isRegularFile(report)) {
            return null;
        }
        try {
            return Files.readString(report);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + report, e);
        }
    }
}
