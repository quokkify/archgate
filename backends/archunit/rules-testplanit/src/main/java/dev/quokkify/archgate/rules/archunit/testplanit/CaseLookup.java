package dev.quokkify.archgate.rules.archunit.testplanit;

import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.cases.archunit.AutomationType;
import dev.quokkify.archgate.cases.archunit.CaseIndex;
import dev.quokkify.archgate.cases.archunit.CaseServiceUnavailableException;
import java.io.UncheckedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

/**
 * Builds the case index from TestPlanIt. It first reads the configured project, because TestPlanIt answers a request
 * it does not authenticate with 200 and no rows instead of 401: without that check a token the server ignores would
 * make every linked case look missing. Then it asks for the linked cases in chunks and sorts each id into existing,
 * missing, deleted or foreign.
 */
final class CaseLookup {

    static final String AUTOMATED = "Automated";
    static final String MANUAL = "Manual";

    private static final String PROJECT_REQUEST = "projects/findUnique";
    private static final String CASES_REQUEST = "repositoryCases/findMany";
    private static final String CASE_FIELDS = "{\"id\":true,\"projectId\":true,\"isDeleted\":true,\"automated\":true}";
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;

    private final TestPlanItClient client;
    private final TestPlanItConnection connection;
    private long deadline;

    CaseLookup(TestPlanItClient client, TestPlanItConnection connection) {
        this.client = Objects.requireNonNull(client, "client");
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    /**
     * Loads the index for the given ids.
     *
     * @throws CaseServiceUnavailableException if TestPlanIt does not answer in time, answers with an error or
     *     answers with something unreadable
     * @throws UnableToVerifyError if TestPlanIt refuses the token or the token cannot see the project
     */
    CaseIndex load(SortedSet<Integer> ids) throws CaseServiceUnavailableException {
        deadline = System.nanoTime() + connection.budget().toNanos();
        try {
            requireProject();
            return classify(ids, rows(ids));
        } catch (TestPlanItApiException e) {
            if (e.status() == UNAUTHORIZED || e.status() == FORBIDDEN) {
                throw new UnableToVerifyError(("TestPlanIt rejected the token (HTTP %d); check that %s is a valid, "
                                + "active API token of a user who may read project %d")
                        .formatted(e.status(), TestPlanItCaseService.TOKEN, connection.projectId()));
            }
            if (e.status() == TOO_MANY_REQUESTS) {
                throw new CaseServiceUnavailableException("TestPlanIt rate limit (429)", e);
            }
            throw new CaseServiceUnavailableException(e.getMessage(), e);
        } catch (UncheckedIOException e) {
            if (System.nanoTime() - deadline >= 0) {
                throw new CaseServiceUnavailableException(
                        "TestPlanIt load exceeded its budget of %d ms"
                                .formatted(connection.budget().toMillis()),
                        e);
            }
            throw new CaseServiceUnavailableException(e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new CaseServiceUnavailableException(e.getMessage(), e);
        }
    }

    private void requireProject() {
        Object project = client.read(
                PROJECT_REQUEST,
                "{\"where\":{\"id\":%d},\"select\":{\"id\":true}}".formatted(connection.projectId()),
                timeout());
        if (Objects.isNull(project)) {
            throw new UnableToVerifyError(("TestPlanIt project %d not found or the token cannot see it; check %s and "
                            + "that %s belongs to a user with access to the project")
                    .formatted(connection.projectId(), TestPlanItCaseService.PROJECT_ID, TestPlanItCaseService.TOKEN));
        }
        if (!(project instanceof Map<?, ?>)) {
            throw new IllegalStateException("TestPlanIt %s data is not an object".formatted(PROJECT_REQUEST));
        }
    }

    private Map<Integer, Row> rows(SortedSet<Integer> ids) {
        List<Integer> all = new ArrayList<>(ids);
        Map<Integer, Row> rows = new HashMap<>();
        for (int from = 0; from < all.size(); from += connection.chunkSize()) {
            List<Integer> chunk = all.subList(from, Math.min(all.size(), from + connection.chunkSize()));
            String in = chunk.stream().map(String::valueOf).collect(Collectors.joining(","));
            Object data = client.read(
                    CASES_REQUEST,
                    "{\"where\":{\"id\":{\"in\":[%s]}},\"select\":%s}".formatted(in, CASE_FIELDS),
                    timeout());
            if (!(data instanceof List<?> list)) {
                throw new IllegalStateException("TestPlanIt %s data is not a list".formatted(CASES_REQUEST));
            }
            Set<Integer> asked = new HashSet<>(chunk);
            for (Object element : list) {
                Row row = Row.from(element);
                if (!asked.contains(row.id()) || Objects.nonNull(rows.put(row.id(), row))) {
                    throw new IllegalStateException(
                            "TestPlanIt %s returned case %d that was not asked for".formatted(CASES_REQUEST, row.id()));
                }
            }
        }
        return rows;
    }

    private CaseIndex classify(SortedSet<Integer> ids, Map<Integer, Row> rows) {
        Map<Integer, AutomationType> existing = new HashMap<>();
        Set<Integer> missing = new HashSet<>();
        Set<Integer> deleted = new HashSet<>();
        Map<Integer, String> foreign = new HashMap<>();
        for (Integer id : ids) {
            Row row = rows.get(id);
            if (Objects.isNull(row)) {
                missing.add(id);
            } else if (row.deleted()) {
                deleted.add(id);
            } else if (row.projectId() != connection.projectId()) {
                foreign.put(id, String.valueOf(row.projectId()));
            } else {
                existing.put(id, AutomationType.named(row.automated() ? AUTOMATED : MANUAL));
            }
        }
        return new CaseIndex(existing, missing, deleted, foreign);
    }

    private Duration timeout() {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new UncheckedIOException(new HttpTimeoutException("budget spent"));
        }
        Duration left =
                Duration.ofNanos(Math.max(remaining, Duration.ofMillis(1).toNanos()));
        return left.compareTo(connection.readTimeout()) < 0 ? left : connection.readTimeout();
    }

    private record Row(int id, long projectId, boolean deleted, boolean automated) {

        static Row from(Object element) {
            if (!(element instanceof Map<?, ?> object)) {
                throw new IllegalStateException("TestPlanIt %s row is not an object".formatted(CASES_REQUEST));
            }
            long id = number(object, "id");
            if (id < 1 || id > Integer.MAX_VALUE) {
                throw new IllegalStateException("TestPlanIt %s row has an invalid id".formatted(CASES_REQUEST));
            }
            return new Row((int) id, number(object, "projectId"), flag(object, "isDeleted"), flag(object, "automated"));
        }

        private static long number(Map<?, ?> object, String name) {
            if (object.get(name) instanceof Long value) {
                return value;
            }
            throw new IllegalStateException("TestPlanIt %s row has no integer %s".formatted(CASES_REQUEST, name));
        }

        private static boolean flag(Map<?, ?> object, String name) {
            if (object.get(name) instanceof Boolean value) {
                return value;
            }
            throw new IllegalStateException("TestPlanIt %s row has no boolean %s".formatted(CASES_REQUEST, name));
        }
    }
}
