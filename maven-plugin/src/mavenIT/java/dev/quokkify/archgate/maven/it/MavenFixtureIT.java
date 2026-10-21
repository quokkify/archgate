package dev.quokkify.archgate.maven.it;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testing.ForkedMaven;
import dev.quokkify.archgate.testing.MavenCache;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.maven.shared.verifier.VerificationException;
import org.apache.maven.shared.verifier.Verifier;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MavenFixtureIT {

    private static final String IT_REPO = "archgate.itRepo";
    private static final String VERSION = "archgate.version";

    @TempDir
    Path temp;

    static Stream<String> scenarios() {
        return Fixture.scenarios().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void runsScenario(String scenario) throws Exception {
        Path project = Fixture.materialize(
                scenario,
                BuildTool.MAVEN,
                temp.resolve("project"),
                Path.of(System.getProperty(IT_REPO)),
                System.getProperty(VERSION));
        Path localRepo = temp.resolve("m2");
        MavenCache cache = MavenCache.fromSystemProperty();
        try {
            for (Expectation expectation : Expectation.load(Fixture.root().resolve(scenario))) {
                run(project, localRepo, cache, expectation);
            }
        } finally {
            cache.promote(localRepo);
        }
    }

    private static void run(Path project, Path localRepo, MavenCache cache, Expectation expectation) throws Exception {
        Verifier verifier = ForkedMaven.pinJavaHome(new Verifier(project.toString()));
        verifier.setLocalRepo(localRepo.toString());
        verifier.setLogFileName("log-run-" + expectation.run() + ".txt");
        List<String> arguments =
                new ArrayList<>(List.of("-s", "settings.xml", "-Pit-repo", cache.tailArgument(), "verify"));
        arguments.addAll(expectation.args(BuildTool.MAVEN));
        verifier.addCliArguments(arguments.toArray(String[]::new));
        boolean succeeded = true;
        try {
            verifier.execute();
        } catch (VerificationException e) {
            succeeded = false;
        }
        String log = log(project, verifier);
        assertThat(succeeded).as("run %d succeeded%n%s", expectation.run(), log).isEqualTo(expectation.success());
        for (String text : expectation.contains(BuildTool.MAVEN)) {
            assertThat(log).as("run %d", expectation.run()).contains(text);
        }
        for (String text : expectation.absent(BuildTool.MAVEN)) {
            assertThat(log).as("run %d", expectation.run()).doesNotContain(text);
        }
        for (Pattern pattern : expectation.matches(BuildTool.MAVEN)) {
            assertThat(pattern.matcher(log).find())
                    .as("run %d matches %s%n%s", expectation.run(), pattern, log)
                    .isTrue();
        }
    }

    private static String log(Path project, Verifier verifier) throws IOException {
        return Files.readString(project.resolve(verifier.getLogFileName()));
    }
}
