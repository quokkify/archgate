package dev.quokkify.archgate.parity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReportParityTest {

    @TempDir
    Path temp;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"pass", "fail-on-threshold", "multi-module", "multi-module-violation"})
    void sameReportOnMavenAndGradle(String scenario) {
        BuildRun maven = ToolRunners.maven(scenario, temp.resolve("maven"));
        BuildRun gradle = ToolRunners.gradle(scenario, temp.resolve("gradle"));
        List<Expectation> expectations = Expectation.load(Fixture.root().resolve(scenario));
        for (Expectation expectation : expectations) {
            BuildResultView m = maven.run(expectation);
            BuildResultView g = gradle.run(expectation);
            assertThat(m.success())
                    .as("maven success, run %d", expectation.run())
                    .isEqualTo(expectation.success());
            assertThat(g.success())
                    .as("gradle success, run %d", expectation.run())
                    .isEqualTo(m.success());
            assertThat(ReportNormalizer.normalize(g.report()))
                    .as("report of run %d", expectation.run())
                    .isEqualTo(ReportNormalizer.normalize(m.report()));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {"missing-services", "unregistered-rule", "incompatible-rule-library", "missing-context-provider"
            })
    void sameUnableToVerifyMessage(String scenario) {
        BuildRun maven = ToolRunners.maven(scenario, temp.resolve("maven"));
        BuildRun gradle = ToolRunners.gradle(scenario, temp.resolve("gradle"));
        for (Expectation expectation : Expectation.load(Fixture.root().resolve(scenario))) {
            BuildResultView m = maven.run(expectation);
            BuildResultView g = gradle.run(expectation);
            assertThat(m.success()).isFalse();
            assertThat(g.success()).isFalse();
            String needle = expectation.contains(BuildTool.MAVEN).get(0);
            assertThat(ReportNormalizer.messageLine(g.log(), needle))
                    .as("message line containing '%s'", needle)
                    .isEqualTo(ReportNormalizer.messageLine(m.log(), needle));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "5 ms", "123 ms", "1.2 s",
            })
    void normalizesDurations(String duration) {
        assertThat(ReportNormalizer.normalize("took " + duration + " total")).isEqualTo("took N ms total");
    }

    @org.junit.jupiter.api.Test
    void normalizesLineEndings() {
        assertThat(ReportNormalizer.normalize("a\r\nb\rc\n")).isEqualTo("a\nb\nc\n");
    }
}
