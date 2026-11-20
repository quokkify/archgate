package dev.quokkify.archgate.rules.archunit.testrail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/** An in-memory TestRail project with configurable delays and failures, recording every call. */
final class StubCaseSource implements CaseSource {

    static final int PROJECT_ID = 7;
    static final int AUTOMATED = 2;
    static final String AUTOMATION_TYPES = "0, None\n1, Scripted\n2, Automated\n3, To automate\n4, Manual";

    private final Map<Integer, List<CaseRecord>> suites = new HashMap<>();
    private final Map<Integer, CaseRecord> cases = new HashMap<>();
    private final Map<Integer, Integer> foreignSuites = new HashMap<>();
    private final Map<Integer, RuntimeException> suiteProjectFailures = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicInteger> suiteProjectCalls = new ConcurrentHashMap<>();
    private final AtomicInteger projectSuitesCalls = new AtomicInteger();
    private final Map<PageRequest, RuntimeException> suiteFailures = new ConcurrentHashMap<>();
    private final Map<Integer, RuntimeException> caseFailures = new ConcurrentHashMap<>();
    private final List<PageRequest> pageRequests = new CopyOnWriteArrayList<>();
    private final Map<Integer, AtomicInteger> activePagesBySuite = new ConcurrentHashMap<>();
    private final AtomicBoolean pagesOfSuitesOverlapped = new AtomicBoolean();
    private final List<Integer> findCaseIds = new CopyOnWriteArrayList<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger maxActive = new AtomicInteger();
    private final AtomicInteger activeFindCases = new AtomicInteger();
    private final AtomicInteger maxActiveFindCases = new AtomicInteger();
    private final AtomicInteger calls = new AtomicInteger();
    private final Set<Thread> workerThreads = ConcurrentHashMap.newKeySet();
    private volatile RuntimeException projectSuitesFailure;
    private volatile List<FieldRecord> caseFields = List.of(
            new FieldRecord("custom_tags", "1, Red\n2, Blue"),
            new FieldRecord(CaseRecord.AUTOMATION_TYPE_FIELD, AUTOMATION_TYPES));
    private volatile Error workerError;
    private volatile int workerErrorCaseId;
    private volatile Duration caseFieldsDelay = Duration.ZERO;
    private volatile Duration findCaseDelay = Duration.ZERO;
    private volatile Duration suitePageDelay = Duration.ZERO;
    private volatile Duration suiteProjectDelay = Duration.ZERO;
    private volatile Duration projectSuitesDelay = Duration.ZERO;

    StubCaseSource suite(int suiteId, int firstCaseId, int size) {
        return suite(
                suiteId,
                IntStream.range(firstCaseId, firstCaseId + size).boxed().toList());
    }

    StubCaseSource suite(int suiteId, List<Integer> caseIds) {
        List<CaseRecord> suiteCases =
                caseIds.stream().map(id -> record(id, suiteId)).toList();
        suites.put(suiteId, suiteCases);
        suiteCases.forEach(record -> cases.put(record.id(), record));
        return this;
    }

    StubCaseSource foreignSuite(int suiteId, int projectId, int... caseIds) {
        foreignSuites.put(suiteId, projectId);
        IntStream.of(caseIds).forEach(id -> cases.put(id, record(id, suiteId)));
        return this;
    }

    /** A case that lookups find in the suite but the suite's pages do not list. */
    StubCaseSource orphan(int caseId, int suiteId) {
        cases.put(caseId, record(caseId, suiteId));
        return this;
    }

    /** A case that the suite's pages list but a lookup does not find. */
    StubCaseSource unfindable(int caseId) {
        cases.remove(caseId);
        return this;
    }

    StubCaseSource deleted(int caseId, int suiteId) {
        cases.put(caseId, new CaseRecord(caseId, suiteId, true, AUTOMATED));
        return this;
    }

    StubCaseSource withCase(CaseRecord record) {
        cases.put(record.id(), record);
        return this;
    }

    StubCaseSource delays(Duration caseFields, Duration findCase, Duration suitePage) {
        this.caseFieldsDelay = caseFields;
        this.findCaseDelay = findCase;
        this.suitePageDelay = suitePage;
        return this;
    }

    StubCaseSource suiteProjectDelay(Duration delay) {
        this.suiteProjectDelay = delay;
        return this;
    }

    StubCaseSource projectSuitesDelay(Duration delay) {
        this.projectSuitesDelay = delay;
        return this;
    }

    StubCaseSource failProjectSuites(RuntimeException failure) {
        this.projectSuitesFailure = failure;
        return this;
    }

    StubCaseSource failSuiteProject(int suiteId, RuntimeException failure) {
        suiteProjectFailures.put(suiteId, failure);
        return this;
    }

    StubCaseSource failSuitePage(int suiteId, int offset, RuntimeException failure) {
        suiteFailures.put(new PageRequest(suiteId, offset), failure);
        return this;
    }

    StubCaseSource withCaseFields(List<FieldRecord> fields) {
        this.caseFields = fields;
        return this;
    }

    StubCaseSource failFindCaseWithError(int caseId, Error failure) {
        workerError = failure;
        workerErrorCaseId = caseId;
        return this;
    }

    StubCaseSource failFindCase(int caseId, RuntimeException failure) {
        caseFailures.put(caseId, failure);
        return this;
    }

    @Override
    public Optional<CaseRecord> findCase(int id) {
        findCaseIds.add(id);
        if (Objects.nonNull(workerError) && id == workerErrorCaseId) {
            throw workerError;
        }
        maxActiveFindCases.accumulateAndGet(activeFindCases.incrementAndGet(), Math::max);
        try {
            return call(findCaseDelay, caseFailures.get(id), () -> Optional.ofNullable(cases.get(id)));
        } finally {
            activeFindCases.decrementAndGet();
        }
    }

    @Override
    public List<CaseRecord> suitePage(int suiteId, int offset) {
        PageRequest request = new PageRequest(suiteId, offset);
        pageRequests.add(request);
        AtomicInteger own = activePagesBySuite.computeIfAbsent(suiteId, key -> new AtomicInteger());
        own.incrementAndGet();
        if (activePagesBySuite.entrySet().stream()
                .anyMatch(entry -> entry.getKey() != suiteId && entry.getValue().get() > 0)) {
            pagesOfSuitesOverlapped.set(true);
        }
        RuntimeException failure = foreignSuites.containsKey(suiteId)
                ? new TestRailApiException("get_cases/%d&suite_id=%d".formatted(PROJECT_ID, suiteId), 400)
                : suiteFailures.get(request);
        try {
            return call(suitePageDelay, failure, () -> {
                List<CaseRecord> suiteCases = suites.getOrDefault(suiteId, List.of());
                if (offset >= suiteCases.size()) {
                    return List.of();
                }
                return new ArrayList<>(suiteCases.subList(offset, Math.min(offset + PAGE_SIZE, suiteCases.size())));
            });
        } finally {
            own.decrementAndGet();
        }
    }

    @Override
    public List<FieldRecord> caseFields() {
        return call(caseFieldsDelay, null, () -> caseFields);
    }

    @Override
    public Set<Integer> projectSuiteIds() {
        projectSuitesCalls.incrementAndGet();
        return call(projectSuitesDelay, projectSuitesFailure, () -> Set.copyOf(suites.keySet()));
    }

    @Override
    public int projectId() {
        return PROJECT_ID;
    }

    @Override
    public int suiteProjectId(int suiteId) {
        suiteProjectCalls.computeIfAbsent(suiteId, key -> new AtomicInteger()).incrementAndGet();
        return call(suiteProjectDelay, suiteProjectFailures.get(suiteId), () -> {
            Integer projectId = foreignSuites.get(suiteId);
            if (Objects.isNull(projectId)) {
                throw new IllegalStateException("not a foreign suite: " + suiteId);
            }
            return projectId;
        });
    }

    int suiteOf(int caseId) {
        return cases.get(caseId).suiteId();
    }

    Set<Thread> workerThreads() {
        return Set.copyOf(workerThreads);
    }

    int calls() {
        return calls.get();
    }

    int projectSuitesCalls() {
        return projectSuitesCalls.get();
    }

    int suiteProjectCalls() {
        return suiteProjectCalls.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    int suiteProjectCalls(int suiteId) {
        return suiteProjectCalls.getOrDefault(suiteId, new AtomicInteger()).get();
    }

    List<Integer> suitePageOffsets() {
        return pageRequests.stream().map(PageRequest::offset).toList();
    }

    List<Integer> suitePageOffsets(int suiteId) {
        return pageRequests.stream()
                .filter(request -> request.suiteId() == suiteId)
                .map(PageRequest::offset)
                .toList();
    }

    List<Integer> suitePageSuites() {
        return pageRequests.stream().map(PageRequest::suiteId).toList();
    }

    long emptyPages() {
        return pageRequests.stream()
                .filter(request -> request.offset()
                        >= suites.getOrDefault(request.suiteId(), List.of()).size())
                .count();
    }

    boolean pagesOfSuitesOverlapped() {
        return pagesOfSuitesOverlapped.get();
    }

    int maxActiveFindCases() {
        return maxActiveFindCases.get();
    }

    int findCaseCalls() {
        return findCaseIds.size();
    }

    List<Integer> findCaseIds() {
        return List.copyOf(findCaseIds);
    }

    int active() {
        return active.get();
    }

    int maxActive() {
        return maxActive.get();
    }

    private <T> T call(Duration delay, RuntimeException failure, Supplier<T> body) {
        calls.incrementAndGet();
        workerThreads.add(Thread.currentThread());
        int now = active.incrementAndGet();
        maxActive.accumulateAndGet(now, Math::max);
        try {
            if (Objects.nonNull(failure)) {
                throw failure;
            }
            if (!delay.isZero()) {
                Thread.sleep(delay);
            }
            return body.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        } finally {
            active.decrementAndGet();
        }
    }

    private record PageRequest(int suiteId, int offset) {}

    private static CaseRecord record(int id, int suiteId) {
        return new CaseRecord(id, suiteId, false, AUTOMATED);
    }
}
