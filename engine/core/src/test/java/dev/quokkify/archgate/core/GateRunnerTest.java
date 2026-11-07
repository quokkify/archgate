package dev.quokkify.archgate.core;

import static dev.quokkify.archgate.core.TestRules.satisfied;
import static dev.quokkify.archgate.core.TestRules.throwing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.testng.annotations.Test;

class GateRunnerTest {

    private static final ArchitectureViolation BOOM = new ArchitectureViolation("contract", List.of("SomeClass"));

    @Test
    void passingRuleHasNullFinding() {
        GateResult result = GateRunner.run(List.of(satisfied("green", Severity.ERROR)), TestRules.resolver());

        assertThat(result.outcomes()).singleElement().satisfies(outcome -> {
            assertThat(outcome.finding()).isNull();
            assertThat(outcome.rule().name()).isEqualTo("green");
        });
        assertThat(result.worst()).isEmpty();
    }

    @Test
    void violationAndAssertionErrorAreFindings() {
        AssertionError archUnit = new AssertionError("Architecture Violation was violated (1 times)");

        GateResult result = GateRunner.run(
                List.of(throwing("a", Severity.ERROR, BOOM), throwing("b", Severity.WARNING, archUnit)),
                TestRules.resolver());

        assertThat(result.outcomes()).extracting(RuleOutcome::finding).containsExactly(BOOM, archUnit);
        assertThat(result.count(Severity.ERROR)).isEqualTo(1);
        assertThat(result.count(Severity.WARNING)).isEqualTo(1);
        assertThat(result.count(Severity.INFO)).isZero();
        assertThat(result.worst()).contains(Severity.ERROR);
    }

    @Test
    void runtimeExceptionIsUnableToVerify() {
        IllegalStateException cause = new IllegalStateException("broken");

        assertThatThrownBy(() -> GateRunner.run(List.of(throwing("bad", Severity.INFO, cause)), TestRules.resolver()))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("bad")
                .hasCause(cause);
    }

    @Test
    void unableToVerifyFromOneRuleDoesNotStopOthersButIsRethrown() {
        Collection<String> evaluated = new ConcurrentLinkedQueue<>();
        UnableToVerifyError first = new UnableToVerifyError("classpath element missing");
        ArchitectureRule<?> recording = new TestRules("other", Severity.ERROR, ctx -> evaluated.add("other"));

        assertThatThrownBy(() -> GateRunner.run(
                        List.of(
                                throwing("a-unverifiable", Severity.WARNING, first),
                                recording,
                                throwing("z-unverifiable", Severity.WARNING, new UnableToVerifyError("second"))),
                        TestRules.resolver()))
                .isSameAs(first)
                .hasMessageContaining("classpath element missing")
                .satisfies(error -> assertThat(error.getSuppressed()).hasSize(1));
        assertThat(evaluated).containsExactly("other");
    }

    @Test
    void rulesRunConcurrently() {
        CountDownLatch started = new CountDownLatch(2);
        Collection<Boolean> observed = new ConcurrentLinkedQueue<>();
        ArchitectureRule<?> one = new TestRules("one", Severity.ERROR, ctx -> awaitPeer(started, observed));
        ArchitectureRule<?> two = new TestRules("two", Severity.ERROR, ctx -> awaitPeer(started, observed));

        GateResult result = GateRunner.run(List.of(one, two), TestRules.resolver());

        assertThat(observed).containsExactly(true, true);
        assertThat(result.outcomes()).hasSize(2);
    }

    @Test
    void outcomesSortedByRuleName() {
        GateResult result = GateRunner.run(
                List.of(satisfied("c", Severity.ERROR), satisfied("a", Severity.ERROR), satisfied("b", Severity.ERROR)),
                TestRules.resolver());

        assertThat(result.outcomes())
                .extracting(outcome -> outcome.rule().name())
                .containsExactly("a", "b", "c");
    }

    @Test
    void durationsAreMeasured() {
        ArchitectureRule<?> waiting = new TestRules("waiting", Severity.ERROR, ctx -> {
            sleep(150);
        });

        GateResult result = GateRunner.run(List.of(waiting), TestRules.resolver());

        assertThat(result.outcomes().get(0).duration()).isLessThan(Duration.ofSeconds(5));
        assertThat(result.elapsedMillis()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void failsOnThreshold() {
        GateResult warning = GateRunner.run(List.of(throwing("w", Severity.WARNING, BOOM)), TestRules.resolver());

        assertThat(warning.failsOn(Optional.of(Severity.WARNING))).isTrue();
        assertThat(warning.failsOn(Optional.of(Severity.INFO))).isTrue();
        assertThat(warning.failsOn(Optional.of(Severity.ERROR))).isFalse();
        assertThat(warning.failsOn(Optional.empty())).isFalse();
    }

    @Test
    void failsOnNeverIsAlwaysFalse() {
        GateResult error = GateRunner.run(List.of(throwing("e", Severity.ERROR, BOOM)), TestRules.resolver());

        assertThat(error.failsOn(Optional.empty())).isFalse();
    }

    private static void awaitPeer(CountDownLatch started, Collection<Boolean> observed) {
        started.countDown();
        try {
            observed.add(started.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            observed.add(false);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private record NeedsMissingProvider(String name) implements ArchitectureRule<ArchUnitContext> {

        @Override
        public Severity severity() {
            return Severity.ERROR;
        }

        @Override
        public Class<ArchUnitContext> contextType() {
            return ArchUnitContext.class;
        }

        @Override
        public void verify(ArchUnitContext context) {}
    }

    @Test
    void twoRulesSharingAMissingProviderReportOneUnableToVerify() {
        // regression: shared cached failure was self-suppressed by keepFirst
        assertThatThrownBy(() -> GateRunner.run(
                        List.of(new NeedsMissingProvider("a"), new NeedsMissingProvider("b")), TestRules.resolver()))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("No ContextProvider for dev.quokkify.archgate.context.archunit.ArchUnitContext")
                .hasMessageContaining("dev.quokkify.archgate:archgate-context-archunit");
    }

    @Test
    void skippedScopesOfPassAndFindingAreKept() {
        GateResult result = GateRunner.run(
                List.of(skipping("partial pass", context -> {}), skipping("partial finding", context -> {
                    throw BOOM;
                })),
                TestRules.resolver());

        assertThat(result.outcomes())
                .allSatisfy(outcome -> assertThat(outcome.skipped()).containsExactly(Scope.TEST));
        assertThat(result.count(Severity.ERROR)).isEqualTo(1);
    }

    @Test
    void failureWhileAskingForSkippedScopesIsUnableToVerify() {
        ArchitectureRule<TestRules.NoContext> failing = new SkippingRule("broken skip", context -> {}) {
            @Override
            public Set<Scope> skippedScopes(TestRules.NoContext context) {
                throw new IllegalStateException("cannot tell");
            }
        };

        assertThatThrownBy(() -> GateRunner.run(List.of(failing), TestRules.resolver()))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("broken skip")
                .hasRootCauseMessage("cannot tell");
    }

    @Test
    void invalidSkippedScopesAreUnableToVerifyNamingTheRule() {
        Set<Scope> withNull = new HashSet<>();
        withNull.add(null);
        for (Set<Scope> invalid : Arrays.asList(null, withNull, Set.of(Scope.TEST))) {
            ArchitectureRule<TestRules.NoContext> rule = new SkippingRule("bad skip", context -> {}) {
                @Override
                public Set<Scope> scopes() {
                    return Set.of(Scope.MAIN);
                }

                @Override
                public Set<Scope> skippedScopes(TestRules.NoContext context) {
                    return invalid;
                }
            };

            assertThatThrownBy(() -> GateRunner.run(List.of(rule), TestRules.resolver()))
                    .as("skipped scopes %s", invalid)
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessageContaining("Rule 'bad skip'");
        }
    }

    private static ArchitectureRule<?> skipping(String name, Consumer<TestRules.NoContext> body) {
        return new SkippingRule(name, body);
    }

    private static class SkippingRule implements ArchitectureRule<TestRules.NoContext> {

        private final String name;
        private final Consumer<TestRules.NoContext> body;

        SkippingRule(String name, Consumer<TestRules.NoContext> body) {
            this.name = name;
            this.body = body;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Severity severity() {
            return Severity.ERROR;
        }

        @Override
        public Class<TestRules.NoContext> contextType() {
            return TestRules.NoContext.class;
        }

        @Override
        public void verify(TestRules.NoContext context) {
            body.accept(context);
        }

        @Override
        public Set<Scope> skippedScopes(TestRules.NoContext context) {
            return Set.of(Scope.TEST);
        }
    }
}
