package dev.quokkify.archgate.rules.archunit.testrail;

import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.cases.archunit.AutomationType;
import dev.quokkify.archgate.cases.archunit.CaseIndex;
import dev.quokkify.archgate.cases.archunit.CaseServiceUnavailableException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classifies linked case ids against one TestRail project with a bounded number of concurrent requests and a deadline
 * for the whole load.
 *
 * <p>The case fields and the project's suites are read first. While more ids are unresolved than the lookup
 * threshold, a few ids spread over the range are looked up one by one and every suite they reveal is read page by
 * page, which resolves many ids per request. The rest are looked up one by one. A case in a suite outside the project
 * is foreign; its project is read once per suite.
 */
final class IndexLoader {

    /** Most case pages read from one suite; a suite that still returns a full page at this count is cut off. */
    static final int MAX_PAGES_PER_SUITE = 1000;

    private static final int RATE_LIMITED = 429;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final String THREAD_PREFIX = "testrail-index-";

    private final CaseSource source;
    private final int parallelism;
    private final Duration budget;
    private final int lookupThreshold;
    private final String automationField;

    IndexLoader(CaseSource source, int parallelism, Duration budget, int lookupThreshold) {
        this(source, parallelism, budget, lookupThreshold, CaseRecord.AUTOMATION_TYPE_FIELD);
    }

    IndexLoader(CaseSource source, int parallelism, Duration budget, int lookupThreshold, String automationField) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive: " + parallelism);
        }
        if (lookupThreshold < 0) {
            throw new IllegalArgumentException("lookupThreshold must not be negative: " + lookupThreshold);
        }
        this.source = Objects.requireNonNull(source, "source");
        this.parallelism = parallelism;
        this.budget = Objects.requireNonNull(budget, "budget");
        this.lookupThreshold = lookupThreshold;
        this.automationField = Objects.requireNonNull(automationField, "automationField");
    }

    /**
     * Places every id in exactly one category of the index. When the automation type field is missing or its options
     * cannot be read, every existing case gets an {@linkplain AutomationType#unavailable(String) unavailable} type, so
     * the existence check still works and only the automated check fails.
     *
     * @param ids the linked case ids
     * @return the index
     * @throws CaseServiceUnavailableException if TestRail does not answer in time, answers with an error other than a
     *     refused login, answers with something unreadable or contradicts itself
     * @throws UnableToVerifyError if TestRail refuses the credentials
     */
    CaseIndex load(Set<Integer> ids) throws CaseServiceUnavailableException {
        ExecutorService executor = Executors.newFixedThreadPool(
                parallelism, Thread.ofPlatform().daemon().name(THREAD_PREFIX, 0).factory());
        try {
            return new Load(executor, ids).run();
        } finally {
            executor.shutdownNow();
        }
    }

    private final class Load {

        private final ExecutorService executor;
        private final Set<Integer> requested;
        private final long started = System.nanoTime();
        private final long deadline = started + budget.toNanos();
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();
        private final Map<Integer, Integer> automationTypeIds = new HashMap<>();
        private final Set<Integer> missing = new TreeSet<>();
        private final Set<Integer> deleted = new TreeSet<>();
        private final Map<Integer, Integer> pendingForeign = new TreeMap<>();
        private final Set<Integer> loadedSuites = new HashSet<>();
        private Set<Integer> projectSuites = Set.of();

        Load(ExecutorService executor, Set<Integer> requested) {
            this.executor = executor;
            this.requested = Set.copyOf(requested);
        }

        CaseIndex run() throws CaseServiceUnavailableException {
            List<Start> start = await(List.of(
                    () -> new Start.Fields(source.caseFields()),
                    () -> new Start.Suites(Set.copyOf(source.projectSuiteIds()))));
            projectSuites = ((Start.Suites) start.get(1)).suiteIds();
            Optional<AutomationType> unavailable = Optional.empty();
            Map<Integer, String> automationTypeNames = Map.of();
            try {
                automationTypeNames =
                        AutomationTypes.parse(((Start.Fields) start.get(0)).caseFields(), automationField);
            } catch (IllegalStateException unreadable) {
                unavailable = Optional.of(AutomationType.unavailable(unreadable.getMessage()
                        + ". The automation type is read from the case field with system name " + automationField
                        + "; if your TestRail names it differently, pass that name to the constructors of your"
                        + " TestRailCaseAutomatedRule and TestRailCaseExistsRule subclasses"));
            }
            if (projectSuites.isEmpty() && !requested.isEmpty()) {
                throw contradiction("TestRail project %d returned no suites".formatted(source.projectId()));
            }
            TreeSet<Integer> unresolved = new TreeSet<>(requested);
            while (unresolved.size() > lookupThreshold) {
                List<Integer> probes = spread(unresolved);
                probes.forEach(unresolved::remove);
                Set<Integer> discovered = new LinkedHashSet<>();
                for (int suiteId : lookUp(probes)) {
                    if (loadedSuites.add(suiteId)) {
                        discovered.add(suiteId);
                    }
                }
                loadSuites(discovered);
                unresolved.removeAll(automationTypeIds.keySet());
            }
            lookUp(List.copyOf(unresolved));
            Map<Integer, String> foreign = foreign();
            Map<Integer, AutomationType> existing = new HashMap<>();
            Map<Integer, String> names = automationTypeNames;
            Optional<AutomationType> fixed = unavailable;
            automationTypeIds.forEach(
                    (id, typeId) -> existing.put(id, fixed.orElseGet(() -> automationType(typeId, names))));
            return new CaseIndex(existing, missing, deleted, foreign);
        }

        private Map<Integer, String> foreign() throws CaseServiceUnavailableException {
            List<Integer> suiteIds = List.copyOf(new TreeSet<>(pendingForeign.values()));
            List<Callable<Integer>> lookups = new ArrayList<>();
            suiteIds.forEach(suiteId -> lookups.add(() -> source.suiteProjectId(suiteId)));
            List<Integer> projectIds = await(lookups);
            Map<Integer, Integer> projectBySuite = new HashMap<>();
            for (int index = 0; index < suiteIds.size(); index++) {
                if (projectIds.get(index) == source.projectId()) {
                    throw contradiction("TestRail suite %d is not in project %d but belongs to it"
                            .formatted(suiteIds.get(index), source.projectId()));
                }
                projectBySuite.put(suiteIds.get(index), projectIds.get(index));
            }
            Map<Integer, String> foreign = new HashMap<>();
            pendingForeign.forEach((id, suiteId) -> foreign.put(id, String.valueOf(projectBySuite.get(suiteId))));
            return foreign;
        }

        private List<Integer> spread(Set<Integer> unresolved) {
            List<Integer> sorted = List.copyOf(unresolved);
            int stride = Math.max(1, sorted.size() / parallelism);
            List<Integer> probes = new ArrayList<>();
            for (int index = 0; index < sorted.size() && probes.size() < parallelism; index += stride) {
                probes.add(sorted.get(index));
            }
            return probes;
        }

        private void loadSuites(Set<Integer> suiteIds) throws CaseServiceUnavailableException {
            PageQueue queue = new PageQueue(suiteIds);
            CompletionService<Page> completion = new ExecutorCompletionService<>(executor);
            Set<Future<Page>> inFlight = new HashSet<>();
            while (true) {
                while (inFlight.size() < parallelism && queue.hasNext()) {
                    PageRequest request = queue.next();
                    inFlight.add(submit(
                            completion,
                            () -> new Page(
                                    request.suiteId(),
                                    request.offset(),
                                    source.suitePage(request.suiteId(), request.offset()))));
                }
                if (inFlight.isEmpty()) {
                    return;
                }
                Future<Page> done = next(completion, inFlight);
                inFlight.remove(done);
                Page page = result(done, inFlight);
                collect(page);
                try {
                    queue.complete(page);
                } catch (IllegalStateException endless) {
                    cancel(inFlight);
                    throw new CaseServiceUnavailableException(endless.getMessage(), endless);
                }
            }
        }

        /** Takes the requested, not deleted cases of a page, unless a lookup already placed the id elsewhere. */
        private void collect(Page page) {
            page.cases().stream()
                    .filter(found -> requested.contains(found.id()))
                    .filter(found -> !found.deleted())
                    .filter(found -> !missing.contains(found.id())
                            && !deleted.contains(found.id())
                            && !pendingForeign.containsKey(found.id()))
                    .forEach(found -> automationTypeIds.put(found.id(), found.automationTypeId()));
        }

        /** Looks the ids up one by one and returns the project suites of the cases found. */
        private Set<Integer> lookUp(List<Integer> ids) throws CaseServiceUnavailableException {
            List<Callable<Optional<CaseRecord>>> lookups = new ArrayList<>();
            ids.forEach(id -> lookups.add(() -> source.findCase(id)));
            List<Optional<CaseRecord>> found = await(lookups);
            Set<Integer> suites = new LinkedHashSet<>();
            for (int index = 0; index < ids.size(); index++) {
                int id = ids.get(index);
                Optional<CaseRecord> record = found.get(index);
                if (record.filter(CaseRecord::deleted).isPresent()) {
                    deleted.add(id);
                } else if (record.isPresent()) {
                    int suiteId = suiteId(id, record.get());
                    if (projectSuites.contains(suiteId)) {
                        automationTypeIds.put(id, record.get().automationTypeId());
                        suites.add(suiteId);
                    } else {
                        pendingForeign.put(id, suiteId);
                    }
                } else {
                    missing.add(id);
                }
            }
            return suites;
        }

        private <T> List<T> await(List<Callable<T>> calls) throws CaseServiceUnavailableException {
            CompletionService<T> completion = new ExecutorCompletionService<>(executor);
            List<Future<T>> futures = new ArrayList<>();
            calls.forEach(call -> futures.add(submit(completion, call)));
            for (int index = 0; index < futures.size(); index++) {
                result(next(completion, futures), futures);
            }
            List<T> results = new ArrayList<>();
            futures.forEach(future -> results.add(future.resultNow()));
            return results;
        }

        private <T> Future<T> submit(CompletionService<T> completion, Callable<T> call) {
            sent.incrementAndGet();
            return completion.submit(call);
        }

        private <T> Future<T> next(CompletionService<T> completion, Collection<? extends Future<?>> outstanding)
                throws CaseServiceUnavailableException {
            try {
                Future<T> done = completion.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (Objects.isNull(done)) {
                    cancel(outstanding);
                    TimeoutException timeout =
                            new TimeoutException("TestRail project %d index: loaded %d of %d requests in %d s"
                                    .formatted(
                                            source.projectId(),
                                            completed.get(),
                                            sent.get(),
                                            TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)));
                    throw new CaseServiceUnavailableException(timeout.getMessage(), timeout);
                }
                return done;
            } catch (InterruptedException e) {
                throw interrupted(e, outstanding);
            }
        }

        private <T> T result(Future<T> done, Collection<? extends Future<?>> outstanding)
                throws CaseServiceUnavailableException {
            try {
                T value = done.get();
                completed.incrementAndGet();
                return value;
            } catch (ExecutionException e) {
                cancel(outstanding);
                throw failure(e.getCause());
            } catch (InterruptedException e) {
                throw interrupted(e, outstanding);
            }
        }

        private CaseServiceUnavailableException interrupted(
                InterruptedException e, Collection<? extends Future<?>> outstanding) {
            cancel(outstanding);
            Thread.currentThread().interrupt();
            return new CaseServiceUnavailableException("TestRail index loading interrupted", e);
        }

        private void cancel(Collection<? extends Future<?>> futures) {
            futures.forEach(future -> future.cancel(true));
        }

        /**
         * Translates a failed request. A refused login fails the run, since retrying cannot help; a server or network
         * failure, a timeout or an unreadable answer makes TestRail unavailable; anything else is a defect and is
         * rethrown.
         */
        private CaseServiceUnavailableException failure(Throwable cause) {
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof TestRailApiException api) {
                int status = api.statusCode();
                if (status == UNAUTHORIZED || status == FORBIDDEN) {
                    throw new UnableToVerifyError(
                            ("TestRail rejected the credentials (HTTP %d); check that TESTRAIL_USER and "
                                            + "TESTRAIL_PASSWORD (a password or an API key) are valid and that the "
                                            + "user may read project %d")
                                    .formatted(status, source.projectId()),
                            api);
                }
                if (status == RATE_LIMITED) {
                    return new CaseServiceUnavailableException("TestRail rate limit (429)", api);
                }
                return requestFailed(api);
            }
            if (cause instanceof UncheckedIOException || cause instanceof IllegalStateException) {
                return requestFailed(cause);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            return requestFailed(cause);
        }
    }

    private record Page(int suiteId, int offset, List<CaseRecord> cases) {}

    private record PageRequest(int suiteId, int offset) {}

    /**
     * Queue of suite pages still to fetch. Every newly found suite contributes its first page; a full page adds the
     * next one, and a page with fewer cases than requested ends that suite. A full last allowed page fails the load.
     */
    private static final class PageQueue {

        private final Deque<Integer> unstarted;
        private final Map<Integer, Integer> openSuites = new LinkedHashMap<>();
        private final Set<Integer> closedSuites = new HashSet<>();

        PageQueue(Set<Integer> suiteIds) {
            this.unstarted = new ArrayDeque<>(suiteIds);
        }

        boolean hasNext() {
            return !unstarted.isEmpty() || !openSuites.isEmpty();
        }

        PageRequest next() {
            if (unstarted.isEmpty()) {
                int suiteId = openSuites.keySet().iterator().next();
                int offset = openSuites.merge(suiteId, CaseSource.PAGE_SIZE, Integer::sum) - CaseSource.PAGE_SIZE;
                return new PageRequest(suiteId, offset);
            }
            return new PageRequest(unstarted.pollFirst(), 0);
        }

        void complete(Page page) {
            if (page.cases().size() < CaseSource.PAGE_SIZE) {
                openSuites.remove(page.suiteId());
                closedSuites.add(page.suiteId());
            } else if (page.offset() / CaseSource.PAGE_SIZE + 1 >= MAX_PAGES_PER_SUITE) {
                throw new IllegalStateException("TestRail suite %d returned %d full pages without an end"
                        .formatted(page.suiteId(), MAX_PAGES_PER_SUITE));
            } else if (!closedSuites.contains(page.suiteId())) {
                openSuites.putIfAbsent(page.suiteId(), page.offset() + CaseSource.PAGE_SIZE);
            }
        }
    }

    private sealed interface Start {

        record Fields(List<FieldRecord> caseFields) implements Start {}

        record Suites(Set<Integer> suiteIds) implements Start {}
    }

    private static CaseServiceUnavailableException requestFailed(Throwable cause) {
        String reason = Objects.requireNonNullElse(cause.getMessage(), cause.toString());
        return new CaseServiceUnavailableException("TestRail request failed: " + reason, cause);
    }

    private static CaseServiceUnavailableException contradiction(String message) {
        return new CaseServiceUnavailableException(message, new IllegalStateException(message));
    }

    private static int suiteId(int id, CaseRecord record) throws CaseServiceUnavailableException {
        if (Objects.isNull(record.suiteId())) {
            throw new CaseServiceUnavailableException(
                    "TestRail case %d has no suite_id".formatted(id),
                    new IllegalStateException("suite_id is null for case " + id));
        }
        return record.suiteId();
    }

    private static AutomationType automationType(Integer typeId, Map<Integer, String> names) {
        if (Objects.isNull(typeId)) {
            return AutomationType.notSet();
        }
        String name = names.get(typeId);
        return Objects.isNull(name) || name.isBlank()
                ? AutomationType.unknown(String.valueOf(typeId))
                : AutomationType.named(name);
    }
}
