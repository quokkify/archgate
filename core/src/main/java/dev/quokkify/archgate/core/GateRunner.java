package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GateRunner {

    private GateRunner() {}

    public static GateResult run(List<ArchitectureRule<?>> rules, ContextResolver contexts) {
        long startedAt = System.nanoTime();
        List<Evaluation> evaluations = evaluateAll(rules, contexts);
        final long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        UnableToVerifyError unable = null;
        List<RuleOutcome> outcomes = new ArrayList<>();
        for (Evaluation evaluation : evaluations) {
            if (Objects.nonNull(evaluation.unable())) {
                unable = keepFirst(unable, evaluation.unable());
                continue;
            }
            outcomes.add(new RuleOutcome(evaluation.rule(), evaluation.finding(), evaluation.duration()));
        }
        if (Objects.nonNull(unable)) {
            throw unable;
        }
        outcomes.sort(Comparator.comparing(outcome -> outcome.rule().name()));
        return new GateResult(List.copyOf(outcomes), contexts.buildTimes(), elapsedMillis);
    }

    private static List<Evaluation> evaluateAll(List<ArchitectureRule<?>> rules, ContextResolver contexts) {
        List<ArchitectureRule<?>> ordered = rules.stream()
                .sorted(Comparator.comparing(ArchitectureRule::name))
                .toList();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Evaluation>> pending = ordered.stream()
                    .map(rule -> executor.submit(() -> evaluate(rule, contexts)))
                    .toList();
            List<Evaluation> evaluations = new ArrayList<>(ordered.size());
            for (int index = 0; index < ordered.size(); index++) {
                evaluations.add(await(ordered.get(index), pending.get(index)));
            }
            return evaluations;
        }
    }

    private static Evaluation await(ArchitectureRule<?> rule, Future<Evaluation> pending) {
        try {
            return pending.get();
        } catch (ExecutionException failed) {
            return Evaluation.unable(rule, unableToVerify(rule, failed.getCause()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UnableToVerifyError(
                    "Interrupted while verifying '%s', so the run is incomplete.".formatted(rule.name()), interrupted);
        }
    }

    private static Evaluation evaluate(ArchitectureRule<?> rule, ContextResolver contexts) {
        long startedAt = System.nanoTime();
        Throwable finding = null;
        UnableToVerifyError unable = null;
        try {
            verify(rule, contexts);
        } catch (ArchitectureViolation | AssertionError violation) {
            finding = violation;
        } catch (Throwable failure) {
            unable = unableToVerify(rule, failure);
        }
        return new Evaluation(rule, finding, unable, Duration.ofNanos(System.nanoTime() - startedAt));
    }

    private static <C extends AnalysisContext> void verify(ArchitectureRule<C> rule, ContextResolver contexts) {
        rule.verify(contexts.get(rule.contextType()));
    }

    private static UnableToVerifyError unableToVerify(ArchitectureRule<?> rule, Throwable cause) {
        if (cause instanceof UnableToVerifyError error) {
            return error;
        }
        return new UnableToVerifyError(
                "Rule '%s' failed in a way that cannot be reported as a finding: %s".formatted(rule.name(), cause),
                cause);
    }

    private static UnableToVerifyError keepFirst(UnableToVerifyError first, UnableToVerifyError next) {
        if (Objects.isNull(first)) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    private record Evaluation(
            ArchitectureRule<?> rule, Throwable finding, UnableToVerifyError unable, Duration duration) {

        static Evaluation unable(ArchitectureRule<?> rule, UnableToVerifyError error) {
            return new Evaluation(rule, null, error, Duration.ZERO);
        }
    }
}
