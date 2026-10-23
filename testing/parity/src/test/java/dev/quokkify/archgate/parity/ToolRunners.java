package dev.quokkify.archgate.parity;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testing.FixtureSpec;
import dev.quokkify.archgate.testing.GradleRun;
import dev.quokkify.archgate.testing.MavenCache;
import dev.quokkify.archgate.testing.MavenRun;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.shared.verifier.VerificationException;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

final class ToolRunners {

    private static final String IT_REPO = "archgate.itRepo";
    private static final String VERSION = "archgate.version";
    private static final Path MAVEN_REPORT = Path.of("target", "archgate", "report.txt");
    private static final Path GRADLE_REPORT = Path.of("build", "reports", "archgate", "report.txt");

    private ToolRunners() {}

    static BuildRun maven(String scenario, Path target) {
        Path project = materialize(scenario, BuildTool.MAVEN, target);
        Path localRepo = target.resolveSibling("m2");
        Path report = gateDir(scenario, project).resolve(MAVEN_REPORT);
        return expectation -> runMaven(project, localRepo, report, expectation);
    }

    static BuildRun gradle(String scenario, Path target) {
        Path project = materialize(scenario, BuildTool.GRADLE, target);
        Path report = gateDir(scenario, project).resolve(GRADLE_REPORT);
        return expectation -> runGradle(project, report, expectation);
    }

    private static Path materialize(String scenario, BuildTool tool, Path target) {
        return Fixture.materialize(
                scenario, tool, target, Path.of(System.getProperty(IT_REPO)), System.getProperty(VERSION));
    }

    private static Path gateDir(String scenario, Path project) {
        FixtureSpec spec = FixtureSpec.load(Fixture.root().resolve(scenario));
        return spec.gate().map(project::resolve).orElse(project);
    }

    private static BuildResultView runMaven(Path project, Path localRepo, Path report, Expectation expectation) {
        try {
            MavenCache cache = MavenCache.fromSystemProperty();
            MavenRun.Outcome outcome;
            try {
                outcome = MavenRun.execute(project, localRepo, cache, expectation);
            } finally {
                cache.promote(localRepo);
            }
            return new BuildResultView(outcome.succeeded(), outcome.log(), readReport(report), report);
        } catch (VerificationException | IOException e) {
            throw new IllegalStateException("Maven run " + expectation.run() + " could not be executed", e);
        }
    }

    private static BuildResultView runGradle(Path project, Path report, Expectation expectation) {
        GradleRunner runner = GradleRun.runner(project, expectation.args(BuildTool.GRADLE));
        BuildResult result = expectation.success() ? runner.build() : runner.buildAndFail();
        return new BuildResultView(expectation.success(), result.getOutput(), readReport(report), report);
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
