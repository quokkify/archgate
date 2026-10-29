package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.testsupport.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.testng.annotations.Test;

class FixtureSpecTest {

    private static final List<String> REQUIRED_SCENARIOS = List.of(
            "pass",
            "fail-on-threshold",
            "skip",
            "missing-services",
            "unregistered-rule",
            "multi-module",
            "multi-module-violation",
            "incompatible-rule-library",
            "missing-context-provider",
            "javaparser-wildcard",
            "service-registration");

    @Test
    void loadsMultiModuleViolation() {
        FixtureSpec spec = FixtureSpec.load(Fixture.root().resolve("multi-module-violation"));

        assertThat(spec.modules()).containsExactly("api", "tests", "architecture");
        assertThat(spec.gate()).isEqualTo(Optional.of("architecture"));
        assertThat(spec.dependsOn().get("architecture")).containsExactly("api", "tests");
        assertThat(spec.testDependencies()).containsExactly("archgate-rules-archunit");
    }

    @Test
    void loadsRuleLibrary() {
        FixtureSpec spec = FixtureSpec.load(Fixture.root().resolve("incompatible-rule-library"));

        assertThat(spec.ruleLibraries()).containsEntry("rules-lib", "not-a-version");
    }

    @Test
    void everyScenarioHasExpectations() {
        assertThat(Fixture.scenarios()).containsAll(REQUIRED_SCENARIOS);
        for (String scenario : Fixture.scenarios()) {
            assertThat(Expectation.load(Fixture.root().resolve(scenario)))
                    .as(scenario)
                    .isNotEmpty();
        }
    }

    @Test
    void toolScopedExpectationsApplyToTheirToolOnly() {
        Expectation skip = Expectation.load(Fixture.root().resolve("skip")).get(0);

        assertThat(skip.skipped()).isTrue();
        assertThat(skip.contains(BuildTool.MAVEN)).containsExactly("archgate: skipped");
        assertThat(skip.contains(BuildTool.GRADLE)).isEmpty();
        assertThat(skip.args(BuildTool.GRADLE)).containsExactly("-Parchgate.skip");
    }

    @Test
    void unableToVerifyRunsAreFlagged() {
        assertThat(Expectation.load(Fixture.root().resolve("missing-services"))
                        .get(0)
                        .unableToVerify())
                .isTrue();
        assertThat(Expectation.load(Fixture.root().resolve("pass")).get(0).unableToVerify())
                .isFalse();
    }

    @Test
    void failOnThresholdHasTwoRuns() {
        List<Expectation> runs = Expectation.load(Fixture.root().resolve("fail-on-threshold"));

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).success()).isFalse();
        assertThat(runs.get(1).success()).isTrue();
        assertThat(runs.get(1).args(BuildTool.GRADLE)).containsExactly("-Parchgate.failOn=ERROR");
    }

    @Test
    void regexExpectationsLoadWithIntendedPattern() {
        Expectation violation = Expectation.load(Fixture.root().resolve("multi-module-violation"))
                .get(0);
        Expectation pass =
                Expectation.load(Fixture.root().resolve("multi-module")).get(0);

        assertThat(violation.matches(BuildTool.GRADLE)).hasSize(1);
        Pattern error = violation.matches(BuildTool.MAVEN).get(0);
        assertThat(error.pattern()).isEqualTo("\\[ERROR\\]\\s.*No legacy classes");
        assertThat(error.matcher("[ERROR]  No legacy classes (1 violation)").find())
                .isTrue();
        assertThat(error.matcher("[PASS]  No legacy classes").find()).isFalse();
        Pattern ok = pass.matches(BuildTool.GRADLE).get(0);
        assertThat(ok.matcher("[PASS]  No legacy classes").find()).isTrue();
        assertThat(ok.matcher("[ERROR]  No legacy classes").find()).isFalse();
    }

    @Test
    void unknownOrMalformedKeysFailWithKeyAndFile() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = tempDir.path();
            for (String bad : List.of("run.1.contains.a", "run.1.contian.1", "run.2.outcome", "bogus")) {
                Files.writeString(dir.resolve("expected.properties"), "runs=1\nrun.1.outcome=success\n" + bad + "=x\n");

                assertThatThrownBy(() -> Expectation.load(dir))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining(bad)
                        .hasMessageContaining("expected.properties");
            }
        }
    }

    @Test
    void toolScopedAbsentAppliesToItsToolOnly() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path dir = tempDir.path();
            Files.writeString(
                    dir.resolve("expected.properties"),
                    "runs=1\nrun.1.absent.1=both\nrun.1.maven.absent.1=m\nrun.1.gradle.absent.1=g\n"
                            + "run.1.gradle.matches.1=gx\n");
            Expectation expectation = Expectation.load(dir).get(0);

            assertThat(expectation.absent(BuildTool.MAVEN)).containsExactly("both", "m");
            assertThat(expectation.absent(BuildTool.GRADLE)).containsExactly("both", "g");
            assertThat(expectation.matches(BuildTool.MAVEN)).isEmpty();
            assertThat(expectation.matches(BuildTool.GRADLE)).hasSize(1);
        }
    }
}
