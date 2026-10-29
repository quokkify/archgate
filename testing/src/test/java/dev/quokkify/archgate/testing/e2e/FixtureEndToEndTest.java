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
import dev.quokkify.archgate.testing.ScenarioLog;
import dev.quokkify.archgate.testsupport.TempDir;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.executor.ExecutorException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildResultException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class FixtureEndToEndTest {

    private static final Logger LOG = LogManager.getLogger(FixtureEndToEndTest.class);
    private static final Set<BuildTool> TOOLS = FixtureTools.fromSystemProperty();
    private static final Set<String> NOT_COMPARED = Set.of("skip");
    private static final Path MAVEN_REPORT = Path.of("target", "archgate", "report.txt");
    private static final Path GRADLE_REPORT = Path.of("build", "reports", "archgate", "report.txt");

    @DataProvider(name = "scenarios", parallel = true)
    static Object[][] scenarios() {
        return Fixture.scenarios().stream()
                .map(scenario -> new Object[] {scenario})
                .toArray(Object[][]::new);
    }

    static Stream<String> comparedScenarios() {
        return Fixture.scenarios().stream().filter(scenario -> !NOT_COMPARED.contains(scenario));
    }

    @Test
    void comparesEveryScenarioButSkip() {
        assertThat(comparedScenarios())
                .doesNotContain("skip")
                .contains("javaparser-wildcard", "service-registration", "missing-services");
    }

    @Test(dataProvider = "scenarios")
    void runsScenario(String scenario) {
        boolean compared = TOOLS.size() == BuildTool.values().length && !NOT_COMPARED.contains(scenario);
        List<Expectation> expectations = Expectation.load(Fixture.root().resolve(scenario));
        if (compared) {
            assertThat(expectations)
                    .as("scenario %s needs a run with contains entries to prove the result", scenario)
                    .anyMatch(expectation -> !expectation.contains(MAVEN).isEmpty());
        }
        String gate = FixtureSpec.load(Fixture.root().resolve(scenario)).gate().orElse("");
        try (TempDir temp = TempDir.create()) {
            Map<BuildTool, Path> projects = new EnumMap<>(BuildTool.class);
            for (BuildTool tool : TOOLS) {
                projects.put(
                        tool,
                        ScenarioLog.step(
                                "materialize " + ScenarioLog.toolName(tool),
                                () -> Fixture.materialize(scenario, tool, temp.resolve(tool.name()))));
            }
            for (Expectation expectation : expectations) {
                Map<BuildTool, BuildResultView> results = new EnumMap<>(BuildTool.class);
                if (TOOLS.contains(MAVEN)) {
                    results.put(
                            MAVEN,
                            logged(
                                    scenario,
                                    MAVEN,
                                    expectation,
                                    () -> runMaven(temp.path(), projects.get(MAVEN), gate, expectation)));
                }
                if (TOOLS.contains(GRADLE)) {
                    results.put(
                            GRADLE,
                            logged(
                                    scenario,
                                    GRADLE,
                                    expectation,
                                    () -> runGradle(projects.get(GRADLE), gate, expectation)));
                }
                if (compared) {
                    String comparison = expectation.unableToVerify() ? "compare message #" : "compare reports #";
                    ScenarioLog.step(comparison + expectation.run(), () -> {
                        try {
                            assertSameResult(scenario, expectation, results.get(MAVEN), results.get(GRADLE));
                        } catch (AssertionError | RuntimeException e) {
                            LOG.warn(
                                    "compare failed, see {} and {}",
                                    ScenarioLog.logFile(scenario, MAVEN, expectation.run())
                                            .toAbsolutePath(),
                                    ScenarioLog.logFile(scenario, GRADLE, expectation.run())
                                            .toAbsolutePath());
                            throw e;
                        }
                        return null;
                    });
                }
            }
        }
    }

    private static BuildResultView logged(
            String scenario, BuildTool tool, Expectation expectation, Supplier<BuildResultView> run) {
        return ScenarioLog.run(scenario, tool, expectation.run(), () -> {
            try {
                return run.get();
            } catch (AssertionError | RuntimeException e) {
                Path logFile =
                        ScenarioLog.logFile(scenario, tool, expectation.run()).toAbsolutePath();
                LOG.warn("log file: {}", logFile);
                attach(e, new SeeLogFile(logFile));
                throw e;
            }
        });
    }

    // Gradle's console prints the causes of a failure but not its suppressed exceptions; the message stays unchanged.
    private static void attach(Throwable failure, SeeLogFile reference) {
        if (Objects.isNull(failure.getCause())) {
            try {
                failure.initCause(reference);
                return;
            } catch (IllegalStateException e) {
                // the cause was explicitly set to null
            }
        }
        failure.addSuppressed(reference);
    }

    private static BuildResultView runMaven(Path temp, Path project, String gate, Expectation expectation) {
        Path localRepo = temp.resolve("m2");
        MavenCache cache = MavenCache.fromSystemProperty();
        MavenRun.Outcome outcome;
        try {
            outcome = MavenRun.execute(project, localRepo, cache, expectation);
        } catch (ExecutorException e) {
            throw new IllegalStateException("Maven run " + expectation.run() + " could not be executed", e);
        } finally {
            cache.promote(localRepo);
        }
        boolean succeeded = outcome.succeeded();
        String log = outcome.log();
        ScenarioLog.output(log);
        LOG.info("maven run {} {}", expectation.run(), succeeded ? "succeeded" : "failed");
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
        BuildResult result;
        try {
            result = expectation.success() ? runner.build() : runner.buildAndFail();
        } catch (UnexpectedBuildResultException e) {
            ScenarioLog.output(e.getBuildResult().getOutput());
            throw e;
        }
        String output = result.getOutput();
        ScenarioLog.output(output);
        LOG.info("gradle run {} {}", expectation.run(), expectation.success() ? "succeeded" : "failed");
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

    private static final class SeeLogFile extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SeeLogFile(Path logFile) {
            super("log file: " + logFile, null, false, false);
        }
    }
}
