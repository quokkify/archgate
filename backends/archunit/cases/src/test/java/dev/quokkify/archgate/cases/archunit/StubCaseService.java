package dev.quokkify.archgate.cases.archunit;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** A {@link CaseService} that counts its loads and answers from a configurable function of the query. */
final class StubCaseService implements CaseService {

    static final String NAME = "Tracker";
    static final List<String> VARIABLES = List.of("TRACKER_USER", "TRACKER_TOKEN");
    static final Map<String, String> CREDENTIALS = Map.of("TRACKER_USER", "user", "TRACKER_TOKEN", "token");

    private final List<CaseQuery> queries = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> variables = new CopyOnWriteArrayList<>();
    private final Function<CaseQuery, CaseIndex> answer;
    private final Duration delay;
    private final String displayName;
    private final List<String> requiredVariables;
    private final Object shareKey;
    private volatile CaseServiceUnavailableException unavailable;
    private volatile RuntimeException failure;
    private volatile Error error;

    private StubCaseService(
            String displayName,
            List<String> requiredVariables,
            Function<CaseQuery, CaseIndex> answer,
            Duration delay,
            Object shareKey) {
        this.displayName = displayName;
        this.shareKey = shareKey;
        this.requiredVariables = requiredVariables;
        this.answer = answer;
        this.delay = delay;
    }

    /** Every queried id exists with the automation type named {@code Automated}. */
    static StubCaseService allExisting() {
        return answering(query -> {
            Map<Integer, AutomationType> existing = new HashMap<>();
            query.ids().forEach(id -> existing.put(id, AutomationType.named("Automated")));
            return new CaseIndex(existing, Set.of(), Set.of(), Map.of());
        });
    }

    static StubCaseService answering(Function<CaseQuery, CaseIndex> answer) {
        return new StubCaseService(NAME, VARIABLES, answer, Duration.ZERO, null);
    }

    static StubCaseService fixed(CaseIndex index) {
        return answering(query -> index);
    }

    StubCaseService delayed(Duration pause) {
        return new StubCaseService(displayName, requiredVariables, answer, pause, shareKey);
    }

    StubCaseService withVariables(List<String> names) {
        return new StubCaseService(displayName, names, answer, delay, shareKey);
    }

    StubCaseService named(String name) {
        return new StubCaseService(name, requiredVariables, answer, delay, shareKey);
    }

    /** The same service with a setting of its own, reported through {@link #shareKey()}. */
    StubCaseService configured(Object setting) {
        return new StubCaseService(displayName, requiredVariables, answer, delay, setting);
    }

    StubCaseService failingUnavailable(String message) {
        this.unavailable = new CaseServiceUnavailableException(message, new IllegalStateException("cause"));
        return this;
    }

    StubCaseService failing(RuntimeException error) {
        this.failure = error;
        return this;
    }

    StubCaseService failingWith(Error thrown) {
        this.error = thrown;
        return this;
    }

    int loads() {
        return queries.size();
    }

    List<CaseQuery> queries() {
        return List.copyOf(queries);
    }

    List<Map<String, String>> variables() {
        return List.copyOf(variables);
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Object shareKey() {
        return Objects.isNull(shareKey) ? CaseService.super.shareKey() : shareKey;
    }

    @Override
    public List<String> requiredVariables() {
        return requiredVariables;
    }

    @Override
    public CaseIndex load(CaseQuery query, Map<String, String> values) throws CaseServiceUnavailableException {
        queries.add(query);
        variables.add(values);
        pause();
        if (Objects.nonNull(unavailable)) {
            throw unavailable;
        }
        if (Objects.nonNull(failure)) {
            throw failure;
        }
        if (Objects.nonNull(error)) {
            throw error;
        }
        return answer.apply(query);
    }

    private void pause() {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }
}
