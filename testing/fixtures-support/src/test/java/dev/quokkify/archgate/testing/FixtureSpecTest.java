package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
            "missing-context-provider");

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
    void failOnThresholdHasTwoRuns() {
        List<Expectation> runs = Expectation.load(Fixture.root().resolve("fail-on-threshold"));

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).success()).isFalse();
        assertThat(runs.get(1).success()).isTrue();
        assertThat(runs.get(1).args(BuildTool.GRADLE)).containsExactly("-Parchgate.failOn=ERROR");
    }
}
