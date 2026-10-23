package dev.quokkify.archgate.maven.it;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import dev.quokkify.archgate.testing.MavenCache;
import dev.quokkify.archgate.testing.MavenRun;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
        MavenRun.Outcome outcome = MavenRun.execute(project, localRepo, cache, expectation);
        boolean succeeded = outcome.succeeded();
        String log = outcome.log();
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
}
