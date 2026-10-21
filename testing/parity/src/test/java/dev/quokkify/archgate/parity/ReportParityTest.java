package dev.quokkify.archgate.parity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.testing.BuildTool;
import dev.quokkify.archgate.testing.Expectation;
import dev.quokkify.archgate.testing.Fixture;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReportParityTest {

    @TempDir
    Path temp;

    private static final Set<String> NOT_COMPARED = Set.of("skip");

    static Stream<String> scenarios() {
        return Fixture.scenarios().stream().filter(scenario -> !NOT_COMPARED.contains(scenario));
    }

    @Test
    void comparesEveryScenarioButSkip() {
        assertThat(scenarios())
                .doesNotContain("skip")
                .contains("javaparser-wildcard", "service-registration", "missing-services");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void sameResultOnMavenAndGradle(String scenario) {
        BuildRun maven = ToolRunners.maven(scenario, temp.resolve("maven"));
        BuildRun gradle = ToolRunners.gradle(scenario, temp.resolve("gradle"));
        List<Expectation> expectations = Expectation.load(Fixture.root().resolve(scenario));
        assertThat(expectations)
                .as("scenario %s needs a run with contains entries to prove the result", scenario)
                .anyMatch(expectation -> !expectation.contains(BuildTool.MAVEN).isEmpty());
        for (Expectation expectation : expectations) {
            BuildResultView m = maven.run(expectation);
            BuildResultView g = gradle.run(expectation);
            assertThat(m.success())
                    .as("maven success, run %d", expectation.run())
                    .isEqualTo(expectation.success());
            assertThat(g.success())
                    .as("gradle success, run %d", expectation.run())
                    .isEqualTo(m.success());
            if (expectation.unableToVerify()) {
                assertSameMessage(scenario, expectation, m, g);
            } else {
                assertSameReport(expectation, m, g);
            }
        }
    }

    private static void assertSameReport(Expectation expectation, BuildResultView m, BuildResultView g) {
        assertThat(m.reportPath()).as("Maven report %s", m.reportPath()).isRegularFile();
        assertThat(g.reportPath()).as("Gradle report %s", g.reportPath()).isRegularFile();
        String[] expected = expectation.contains(BuildTool.MAVEN).toArray(String[]::new);
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
        assertThat(expectation.contains(BuildTool.MAVEN))
                .as("scenario %s needs a contains entry to locate the message", scenario)
                .isNotEmpty();
        String needle = expectation.contains(BuildTool.MAVEN).get(0);
        assertThat(ReportNormalizer.messageLine(g.log(), needle))
                .as("message line containing '%s'", needle)
                .isEqualTo(ReportNormalizer.messageLine(m.log(), needle));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "5 ms", "123 ms", "1.2 s",
            })
    void normalizesDurations(String duration) {
        assertThat(ReportNormalizer.normalize("took " + duration + " total")).isEqualTo("took N ms total");
    }

    @Test
    void normalizesLineEndings() {
        assertThat(ReportNormalizer.normalize("a\r\nb\rc\n")).isEqualTo("a\nb\nc\n");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"5 rules, 12 findings", "1.2 services", "3 msgs", "v1.2 sec"})
    void leavesNonDurationsUnchanged(String text) {
        assertThat(ReportNormalizer.normalize(text)).isEqualTo(text);
    }
}
