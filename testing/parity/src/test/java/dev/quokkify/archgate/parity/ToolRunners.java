package dev.quokkify.archgate.parity;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testing.FixtureSpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
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
            Verifier verifier = new Verifier(project.toString());
            verifier.setLocalRepo(localRepo.toString());
            verifier.setLogFileName("log-run-" + expectation.run() + ".txt");
            List<String> arguments = new ArrayList<>(List.of("-s", "settings.xml", "-Pit-repo", "verify"));
            arguments.addAll(expectation.args(BuildTool.MAVEN));
            verifier.addCliArguments(arguments.toArray(String[]::new));
            boolean succeeded = true;
            try {
                verifier.execute();
            } catch (VerificationException e) {
                succeeded = false;
            }
            return new BuildResultView(
                    succeeded, Files.readString(project.resolve(verifier.getLogFileName())), readReport(report));
        } catch (VerificationException | IOException e) {
            throw new IllegalStateException("Maven run " + expectation.run() + " could not be executed", e);
        }
    }

    private static BuildResultView runGradle(Path project, Path report, Expectation expectation) {
        List<String> arguments = new ArrayList<>(List.of("check", "--stacktrace"));
        arguments.addAll(expectation.args(BuildTool.GRADLE));
        GradleRunner runner = GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
        BuildResult result = expectation.success() ? runner.build() : runner.buildAndFail();
        return new BuildResultView(expectation.success(), result.getOutput(), readReport(report));
    }

    private static String readReport(Path report) {
        if (!Files.isRegularFile(report)) {
            return "";
        }
        try {
            return Files.readString(report);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + report, e);
        }
    }
}
