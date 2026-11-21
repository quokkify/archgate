package dev.quokkify.archgate.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.executor.ExecutorRequest;
import org.apache.maven.executor.ExecutorResult;
import org.apache.maven.executor.forked.ForkedMavenExecutor;

public final class MavenRun {

    private MavenRun() {}

    public record Outcome(boolean succeeded, String log) {}

    /** Runs one expectation; the caller promotes {@code cache} afterwards. */
    public static Outcome execute(Path project, Path localRepo, MavenCache cache, Expectation expectation) {
        List<String> goals = new ArrayList<>(List.of("org.apache.maven.plugins:maven-clean-plugin:clean", "verify"));
        goals.addAll(expectation.args(BuildTool.MAVEN));
        return execute(project, localRepo, cache, goals);
    }

    /** Runs the given goals and arguments with the fixture settings; the caller promotes {@code cache} afterwards. */
    public static Outcome execute(Path project, Path localRepo, MavenCache cache, List<String> goals) {
        List<String> arguments = new ArrayList<>(List.of(
                "-e",
                "--batch-mode",
                "-Dmaven.repo.local=" + localRepo,
                "-s",
                "settings.xml",
                "-Pit-repo",
                cache.tailArgument()));
        arguments.addAll(goals);
        ExecutorRequest request = ForkedMaven.pinJavaHome(ExecutorRequest.mavenBuilder())
                .cwd(project)
                .arguments(arguments)
                .build();
        try (ForkedMavenExecutor maven = new ForkedMavenExecutor(Path.of(System.getProperty("maven.home")))) {
            ExecutorResult result = maven.execute(request);
            return new Outcome(
                    result.success(),
                    result.stdOutString().orElse("") + result.stdErrString().orElse(""));
        }
    }
}
