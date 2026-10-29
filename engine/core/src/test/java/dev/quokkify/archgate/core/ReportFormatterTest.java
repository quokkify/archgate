package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.testng.annotations.Test;

class ReportFormatterTest {

    private static final ArchitectureViolation BOOM = new ArchitectureViolation("contract", List.of("SomeClass"));

    @Test
    void passRowHasFixedLayout() {
        ArchitectureRule<?> rule = TestRules.satisfied("green rule", Severity.ERROR);
        GateResult result = result(new RuleOutcome(rule, null, Duration.ofMillis(12)));

        assertThat(ReportFormatter.format(result, Optional.of(Severity.WARNING)))
                .contains(String.format("%-7s %-9s %6d ms  %s", "[PASS]", "MAIN+TEST", 12, "green rule"));
    }

    @Test
    void findingRowUsesSeverityLabelAndIndentedMessage() {
        GateResult result = result(
                new RuleOutcome(TestRules.satisfied("e", Severity.ERROR), BOOM, Duration.ofMillis(1)),
                new RuleOutcome(TestRules.satisfied("w", Severity.WARNING), BOOM, Duration.ofMillis(2)),
                new RuleOutcome(TestRules.satisfied("i", Severity.INFO), BOOM, Duration.ofMillis(3)));

        List<String> lines = ReportFormatter.format(result, Optional.empty());

        assertThat(lines).anyMatch(line -> line.startsWith("[ERROR] MAIN+TEST") && line.endsWith("  e"));
        assertThat(lines).anyMatch(line -> line.startsWith("[WARNING] MAIN+TEST") && line.endsWith("  w"));
        assertThat(lines).anyMatch(line -> line.startsWith("[INFO]") && line.endsWith("  i"));
        assertThat(lines).contains("    contract", "    Violations (1):", "        - SomeClass");
    }

    @Test
    void failuresAreListedAfterPasses() {
        RuleOutcome pass = new RuleOutcome(TestRules.satisfied("a pass", Severity.ERROR), null, Duration.ZERO);
        RuleOutcome failure = new RuleOutcome(TestRules.satisfied("z fail", Severity.WARNING), BOOM, Duration.ZERO);

        List<String> lines = ReportFormatter.format(result(pass, failure), Optional.of(Severity.WARNING));

        assertThat(lines)
                .containsSubsequence(
                        String.format("%-7s %-9s %6d ms  %s", "[PASS]", "MAIN+TEST", 0, "a pass"),
                        String.format("%-7s %-9s %6d ms  %s", "[WARNING]", "MAIN+TEST", 0, "z fail"));
    }

    @Test
    void headerShowsRuleCountAndTotalTime() {
        GateResult slow = new GateResult(
                List.of(new RuleOutcome(TestRules.satisfied("r", Severity.INFO), null, Duration.ZERO)),
                Map.of(),
                23_900);
        GateResult fast = result(new RuleOutcome(TestRules.satisfied("r", Severity.INFO), null, Duration.ZERO));

        assertThat(ReportFormatter.format(slow, Optional.empty()))
                .contains("Architecture verification (1 rule, 23.9 s total)");
        assertThat(ReportFormatter.format(fast, Optional.empty()))
                .contains("Architecture verification (1 rule, 7 ms total)");
    }

    @Test
    void verdictLineReflectsThreshold() {
        GateResult result = result(
                new RuleOutcome(TestRules.satisfied("ok", Severity.ERROR), null, Duration.ZERO),
                new RuleOutcome(TestRules.satisfied("w", Severity.WARNING), BOOM, Duration.ZERO));

        assertThat(ReportFormatter.format(result, Optional.of(Severity.WARNING)))
                .contains("Gate FAILED: 2 rules, 1 warning");
        assertThat(ReportFormatter.format(result, Optional.of(Severity.ERROR)))
                .contains("Gate PASSED: 2 rules, 1 warning");
        assertThat(ReportFormatter.format(result, Optional.empty())).contains("Gate PASSED: 2 rules, 1 warning");
    }

    @Test
    void countsOmitZeroSeverities() {
        GateResult clean = result(new RuleOutcome(TestRules.satisfied("ok", Severity.ERROR), null, Duration.ZERO));
        GateResult mixed = result(
                new RuleOutcome(TestRules.satisfied("e", Severity.ERROR), BOOM, Duration.ZERO),
                new RuleOutcome(TestRules.satisfied("e2", Severity.ERROR), BOOM, Duration.ZERO),
                new RuleOutcome(TestRules.satisfied("w", Severity.WARNING), BOOM, Duration.ZERO));

        assertThat(ReportFormatter.counts(clean)).isEqualTo("1 rule, no findings");
        assertThat(ReportFormatter.counts(mixed)).isEqualTo("3 rules, 2 errors, 1 warning");
    }

    @Test
    void policyTableReflectsThreshold() {
        List<String> lines = ReportFormatter.format(result(), Optional.of(Severity.WARNING));

        assertThat(lines)
                .containsSubsequence(
                        "Failure policy (failOn=WARNING):",
                        "  ERROR    fails the build",
                        "  WARNING  fails the build",
                        "  INFO     reported only");
    }

    @Test
    void neverThresholdHasSingleLinePolicy() {
        List<String> lines = ReportFormatter.format(result(), Optional.empty());

        assertThat(lines)
                .contains("Failure policy (failOn=NEVER): findings are reported, the build never fails")
                .noneMatch(line -> line.startsWith("  ERROR") || line.startsWith("  INFO"));
    }

    @Test
    void scopeIsListedInOrder() {
        ArchitectureRule<?> mainOnly = new ArchitectureRule<TestRules.NoContext>() {
            @Override
            public String name() {
                return "main-only";
            }

            @Override
            public Severity severity() {
                return Severity.ERROR;
            }

            @Override
            public Set<Scope> scopes() {
                return Set.of(Scope.MAIN);
            }

            @Override
            public Class<TestRules.NoContext> contextType() {
                return TestRules.NoContext.class;
            }

            @Override
            public void verify(TestRules.NoContext context) {}
        };

        assertThat(ReportFormatter.format(result(new RuleOutcome(mainOnly, null, Duration.ZERO)), Optional.empty()))
                .anyMatch(line -> line.startsWith("[PASS]") && line.contains(" MAIN "));
    }

    @Test
    void modelBuildTimesAreSummarized() {
        GateResult result =
                new GateResult(List.of(), Map.of("main", Duration.ofMillis(5), "all", Duration.ofMillis(3)), 5);

        assertThat(ReportFormatter.format(result, Optional.empty())).contains("Model build: all 3 ms, main 5 ms");
    }

    private static GateResult result(RuleOutcome... outcomes) {
        return new GateResult(List.of(outcomes), Map.of(), 7);
    }
}
