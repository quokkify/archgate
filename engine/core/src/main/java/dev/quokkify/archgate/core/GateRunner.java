package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
            outcomes.add(new RuleOutcome(
                    evaluation.rule(), evaluation.finding(), evaluation.duration(), evaluation.skipped()));
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

    private static <C extends AnalysisContext> Evaluation evaluate(ArchitectureRule<C> rule, ContextResolver contexts) {
        long startedAt = System.nanoTime();
        try {
            C context = contexts.get(rule.contextType());
            Throwable finding = null;
            try {
                rule.verify(context);
            } catch (ArchitectureViolation | AssertionError violation) {
                finding = violation;
            }
            return new Evaluation(rule, finding, skippedScopes(rule, context), null, since(startedAt));
        } catch (Throwable failure) {
            return new Evaluation(rule, null, Set.of(), unableToVerify(rule, failure), since(startedAt));
        }
    }

    private static <C extends AnalysisContext> Set<Scope> skippedScopes(ArchitectureRule<C> rule, C context) {
        Set<Scope> skipped = rule.skippedScopes(context);
        if (Objects.isNull(skipped) || skipped.stream().anyMatch(Objects::isNull)) {
            throw new UnableToVerifyError(
                    "Rule '%s' returned no valid skipped scopes (%s), so it is unclear what it checked."
                            .formatted(rule.name(), skipped));
        }
        if (!rule.scopes().containsAll(skipped)) {
            throw new UnableToVerifyError(
                    "Rule '%s' reports skipped scopes %s outside its scopes %s, so it is unclear what it checked."
                            .formatted(rule.name(), skipped, rule.scopes()));
        }
        return Set.copyOf(skipped);
    }

    private static Duration since(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
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
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    private record Evaluation(
            ArchitectureRule<?> rule,
            Throwable finding,
            Set<Scope> skipped,
            UnableToVerifyError unable,
            Duration duration) {

        static Evaluation unable(ArchitectureRule<?> rule, UnableToVerifyError error) {
            return new Evaluation(rule, null, Set.of(), error, Duration.ZERO);
        }
    }
}
