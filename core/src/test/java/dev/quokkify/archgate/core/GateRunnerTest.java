package dev.quokkify.archgate.core;

import static dev.quokkify.archgate.core.TestRules.satisfied;
import static dev.quokkify.archgate.core.TestRules.throwing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class GateRunnerTest {

    private static final ArchitectureViolation BOOM = new ArchitectureViolation("contract", List.of("SomeClass"));

    private final ContextResolver context = TestRules.resolver();

    @Test
    void passingRuleHasNullFinding() {
        GateResult result = GateRunner.run(List.of(satisfied("green", Severity.ERROR)), context);

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
                List.of(throwing("a", Severity.ERROR, BOOM), throwing("b", Severity.WARNING, archUnit)), context);

        assertThat(result.outcomes()).extracting(RuleOutcome::finding).containsExactly(BOOM, archUnit);
        assertThat(result.count(Severity.ERROR)).isEqualTo(1);
        assertThat(result.count(Severity.WARNING)).isEqualTo(1);
        assertThat(result.count(Severity.INFO)).isZero();
        assertThat(result.worst()).contains(Severity.ERROR);
    }

    @Test
    void runtimeExceptionIsUnableToVerify() {
        IllegalStateException cause = new IllegalStateException("broken");

        assertThatThrownBy(() -> GateRunner.run(List.of(throwing("bad", Severity.INFO, cause)), context))
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
                        context))
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

        GateResult result = GateRunner.run(List.of(one, two), context);

        assertThat(observed).containsExactly(true, true);
        assertThat(result.outcomes()).hasSize(2);
    }

    @Test
    void outcomesSortedByRuleName() {
        GateResult result = GateRunner.run(
                List.of(satisfied("c", Severity.ERROR), satisfied("a", Severity.ERROR), satisfied("b", Severity.ERROR)),
                context);

        assertThat(result.outcomes())
                .extracting(outcome -> outcome.rule().name())
                .containsExactly("a", "b", "c");
    }

    @Test
    void durationsAreMeasured() {
        ArchitectureRule<?> waiting = new TestRules("waiting", Severity.ERROR, ctx -> {
            sleep(150);
        });

        GateResult result = GateRunner.run(List.of(waiting), context);

        assertThat(result.outcomes().get(0).duration()).isLessThan(Duration.ofSeconds(5));
        assertThat(result.elapsedMillis()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void failsOnThreshold() {
        GateResult warning = GateRunner.run(List.of(throwing("w", Severity.WARNING, BOOM)), context);

        assertThat(warning.failsOn(Optional.of(Severity.WARNING))).isTrue();
        assertThat(warning.failsOn(Optional.of(Severity.INFO))).isTrue();
        assertThat(warning.failsOn(Optional.of(Severity.ERROR))).isFalse();
        assertThat(warning.failsOn(Optional.empty())).isFalse();
    }

    @Test
    void failsOnNeverIsAlwaysFalse() {
        GateResult error = GateRunner.run(List.of(throwing("e", Severity.ERROR, BOOM)), context);

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
}
