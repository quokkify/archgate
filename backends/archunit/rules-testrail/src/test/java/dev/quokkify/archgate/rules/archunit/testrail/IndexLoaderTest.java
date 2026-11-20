package dev.quokkify.archgate.rules.archunit.testrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.cases.archunit.AutomationType;
import dev.quokkify.archgate.cases.archunit.CaseIndex;
import dev.quokkify.archgate.cases.archunit.CaseServiceUnavailableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.testng.annotations.Test;

class IndexLoaderTest {

    private static final Duration BUDGET = Duration.ofSeconds(5);
    private static final Duration SHORT_BUDGET = Duration.ofMillis(100);
    private static final Duration SLOW = Duration.ofSeconds(2);
    private static final Duration START_DELAY = Duration.ofMillis(50);
    private static final Duration IDLE_WAIT = Duration.ofMillis(200);
    private static final int THRESHOLD = 50;
    private static final long TIMEOUT_MS = 5_000;
    private static final int SUITES_FIRST_ID = 1_000;
    private static final int FOREIGN_SUITE = 77;
    private static final int FOREIGN_PROJECT = 3;
    private static final String FOREIGN_LABEL = "3";
    private static final int FOREIGN_CASE = 1_234_567;
    private static final int LOW_CASES = 100;
    private static final int HIGH_CASES = 2_000_000;
    private static final String PROGRESS = "TestRail project \\d+ index: loaded \\d+ of \\d+ requests in 0 s";

    @Test(timeOut = TIMEOUT_MS, description = "A suite is paged until its first short page")
    void singleSuiteLoadedUntilShortPage() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 510);
        Set<Integer> ids = ids(8, 60, 8);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids);

        assertPagedUpTo(source, 1, 500, 2);
        assertThat(index.existing()).containsOnlyKeys(ids);
        assertThat(index.missing()).isEmpty();
        assertThat(source.findCaseCalls()).isEqualTo(2);
        assertThat(source.calls()).isEqualTo(2 + 2 + source.suitePageOffsets().size());
    }

    @Test(timeOut = TIMEOUT_MS, description = "Ids outside every loaded suite are looked up one by one")
    void idsOutsideLoadedSuitesAreLookedUp() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 300).suite(2, 1_000, 300);
        Set<Integer> ids = ids(1, 60, 5);
        ids.addAll(ids(1_000, 60, 5));
        ids.addAll(Set.of(5_000, 5_001));

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids);

        assertThat(source.findCaseIds()).contains(5_000, 5_001);
        assertThat(index.missing()).containsExactlyInAnyOrder(5_000, 5_001);
        assertThat(source.suitePageSuites()).containsOnly(1, 2);
        assertThat(source.suiteProjectCalls()).isZero();
    }

    @Test(timeOut = TIMEOUT_MS, description = "A suite of exactly 500 cases costs one empty page")
    void emptyPageEndsSuiteMultipleOfPageSize() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 500);

        new IndexLoader(source, 1, BUDGET, THRESHOLD).load(ids(1, 60, 8));

        assertThat(source.suitePageOffsets()).containsExactly(0, 250, 500);
        assertThat(source.emptyPages()).isEqualTo(1);
    }

    @Test(
            timeOut = TIMEOUT_MS,
            description = "Pages requested past the short last page are the empty ones, at most P - 1 per suite")
    void pagesPastShortPageAreEmpty() throws CaseServiceUnavailableException {
        int parallelism = 4;
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 3 * CaseSource.PAGE_SIZE + 10)
                .delays(Duration.ZERO, Duration.ZERO, Duration.ofMillis(20));

        new IndexLoader(source, parallelism, BUDGET, THRESHOLD).load(ids(1, 60, 12));

        int needed = 4;
        assertThat(source.suitePageOffsets().size() - needed).isEqualTo(source.emptyPages());
        assertThat(source.emptyPages()).isBetween(0L, (long) parallelism - 1);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a suite of exactly 500 cases stops on the empty page")
    void stopsOnEmptyPageWhenSuiteIsMultipleOfPageSize() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 500);

        new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids(1, 60, 8));

        assertPagedUpTo(source, 1, 500, 2);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A short non-empty page ends the suite")
    void stopsOnShortPage() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 260);

        new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids(1, 60, 4));

        assertPagedUpTo(source, 1, 250, 2);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A suite smaller than one page costs exactly one page request")
    void tinySuiteRequestsOnlyPageZero() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 100);

        CaseIndex index = new IndexLoader(source, 4, BUDGET, THRESHOLD).load(ids(1, 60, 1));

        assertThat(source.suitePageOffsets()).containsExactly(0);
        assertThat(index.existing()).containsKey(60);
        assertThat(source.calls()).isEqualTo(2 + 4 + 1);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Large suite pages stay P in flight and stop after the first short page")
    void largeSuiteKeepsParallelism() throws CaseServiceUnavailableException {
        StubCaseSource source =
                new StubCaseSource().suite(1, 1, 2_000).delays(Duration.ZERO, Duration.ZERO, Duration.ofMillis(10));
        Set<Integer> ids = ids(1, 60, 33);

        CaseIndex index = new IndexLoader(source, 4, BUDGET, THRESHOLD).load(ids);

        assertPagedUpTo(source, 1, 2_000, 4);
        assertThat(source.maxActive()).isEqualTo(4);
        assertThat(index.existing()).containsOnlyKeys(ids);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Pages of different small suites are requested concurrently")
    void smallSuitesLoadConcurrently() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().delays(Duration.ZERO, Duration.ZERO, Duration.ofMillis(20));
        Set<Integer> ids = contiguousSuites(source, 10, 30);

        CaseIndex index = new IndexLoader(source, 4, BUDGET, THRESHOLD).load(ids);

        assertThat(source.pagesOfSuitesOverlapped()).isTrue();
        assertThat(source.maxActive()).isBetween(2, 4);
        IntStream.rangeClosed(1, 10)
                .forEach(suiteId -> assertThat(source.suitePageOffsets(suiteId)).containsExactly(0));
        assertThat(index.existing()).containsOnlyKeys(ids);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Probes run as parallel batches of at most P lookups")
    void probesRunInParallelBatches() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().delays(Duration.ZERO, Duration.ofMillis(20), Duration.ZERO);
        Set<Integer> ids = contiguousSuites(source, 8, 20);

        CaseIndex index = new IndexLoader(source, 4, BUDGET, THRESHOLD).load(ids);

        assertThat(source.maxActiveFindCases()).isBetween(2, 4);
        IntStream.rangeClosed(1, 8)
                .forEach(suiteId -> assertThat(source.suitePageOffsets(suiteId)).containsExactly(0));
        assertThat(index.existing()).containsOnlyKeys(ids);
    }

    @Test(timeOut = TIMEOUT_MS, description = "One probe batch spread over the id range reaches every contiguous suite")
    void probesSpreadAcrossIdRange() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource();
        Set<Integer> ids = contiguousSuites(source, 8, 50);

        CaseIndex index = new IndexLoader(source, 8, BUDGET, THRESHOLD).load(ids);

        assertThat(source.findCaseIds()).hasSize(8);
        assertThat(source.findCaseIds().stream().map(source::suiteOf))
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        IntStream.rangeClosed(1, 8)
                .forEach(suiteId -> assertThat(source.suitePageOffsets(suiteId)).containsExactly(0));
        assertThat(index.existing()).containsOnlyKeys(ids);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A deleted case found by a probe is deleted, not missing")
    void deletedCaseIsDeletedInProbe() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 100, 60).deleted(5, 1);
        Set<Integer> ids = ids(100, 60, 1);
        ids.add(5);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids);

        assertThat(index.deleted()).containsExactly(5);
        assertThat(index.missing()).isEmpty();
        assertThat(index.existing()).doesNotContainKey(5).containsKey(100);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A deleted case found by a tail lookup is deleted, not missing")
    void deletedCaseIsDeletedInTail() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).deleted(900, 1);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1, 2, 900));

        assertThat(index.deleted()).containsExactly(900);
        assertThat(index.missing()).isEmpty();
        assertThat(index.existing()).doesNotContainKey(900).containsKey(2);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A case TestRail does not return at all is missing, not deleted")
    void absentCaseIsMissingNotDeleted() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1, 2, 900));

        assertThat(index.missing()).containsExactly(900);
        assertThat(index.deleted()).isEmpty();
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a probed id resolves when its suite pages omit it")
    void probedIdResolvesEvenIfAbsentFromPages() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(9, 100, 59).orphan(5, 9);
        Set<Integer> ids = ids(100, 59, 1);
        ids.add(5);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids);

        assertThat(index.existing()).containsOnlyKeys(ids);
        assertThat(source.suitePageSuites()).containsOnly(9);
    }

    @Test(
            timeOut = TIMEOUT_MS,
            description = "Regression guard: a page listing an id a lookup found missing does not make it exist too")
    void pageDoesNotOverrideLookupResult() throws CaseServiceUnavailableException {
        List<Integer> listed = new ArrayList<>(List.of(5));
        listed.addAll(ids(100, 60, 1));
        StubCaseSource source = new StubCaseSource().suite(1, listed).unfindable(5);
        Set<Integer> ids = new TreeSet<>(listed);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids);

        assertThat(source.findCaseIds()).contains(5);
        assertThat(source.suitePageSuites()).containsOnly(1);
        assertThat(index.missing()).containsExactly(5);
        assertThat(index.existing()).doesNotContainKey(5).hasSize(60);
    }

    @Test(timeOut = TIMEOUT_MS, description = "At the threshold only point lookups run")
    void tailUsesPointLookupsAtThreshold() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 100);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids(1, 50, 1));

        assertThat(source.suitePageOffsets()).isEmpty();
        assertThat(source.findCaseCalls()).isEqualTo(50);
        assertThat(index.existing()).containsKey(50);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Above the threshold one suite is loaded")
    void suiteLoadedAboveThreshold() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 100);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids(1, 51, 1));

        assertThat(source.suitePageSuites()).containsExactly(1);
        assertThat(source.findCaseCalls()).isEqualTo(2);
        assertThat(index.existing()).containsKey(51);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Ids unknown to TestRail in the tail are reported as missing")
    void missingIdsCollected() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1, 2, 901, 902, 903));

        assertThat(index.missing()).containsExactlyInAnyOrder(901, 902, 903);
        assertThat(index.existing()).containsOnlyKeys(1, 2);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Ids unknown to TestRail in the suite phase are reported as missing")
    void missingIdsCollectedInSuitePhase() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 100, 60);
        Set<Integer> ids = ids(100, 60, 1);
        ids.addAll(Set.of(1, 2, 3));

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids);

        assertThat(index.missing()).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(source.suitePageSuites()).containsOnly(1);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Every queried id lands in exactly one category")
    void everyIdIsClassified() throws CaseServiceUnavailableException {
        StubCaseSource source = foreignFixture().deleted(50, 1);
        Set<Integer> ids = withForeign(HIGH_CASES);
        ids.addAll(Set.of(50, 51, 104));

        CaseIndex index = new IndexLoader(source, 3, BUDGET, THRESHOLD).load(ids);

        Set<Integer> classified = new TreeSet<>(index.existing().keySet());
        classified.addAll(index.missing());
        classified.addAll(index.deleted());
        classified.addAll(index.foreign().keySet());
        assertThat(classified).isEqualTo(ids);
        assertThat(index.deleted()).containsExactly(50);
        assertThat(index.missing()).containsExactly(51);
        assertThat(index.foreign()).containsOnlyKeys(FOREIGN_CASE);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Concurrent source calls never exceed the parallelism")
    void parallelismNeverExceeded() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1_000, 300)
                .suite(2, 2_000, 300)
                .suite(3, 3_000, 300)
                .suite(4, 4_000, 300)
                .delays(Duration.ofMillis(5), Duration.ofMillis(5), Duration.ofMillis(5));
        Set<Integer> ids = ids(1_000, 50, 1);
        ids.addAll(ids(2_000, 50, 1));
        ids.addAll(ids(3_000, 50, 1));
        ids.addAll(ids(4_000, 50, 1));

        CaseIndex index = new IndexLoader(source, 3, BUDGET, THRESHOLD).load(ids);

        assertThat(source.maxActive()).isBetween(2, 3);
        assertThat(index.existing()).containsOnlyKeys(ids);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Automation types are named from the case fields")
    void automationTypesNamedFromCaseFields() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 10)
                .withCase(new CaseRecord(20, 1, false, 4))
                .withCase(new CaseRecord(21, 1, false, null))
                .withCase(new CaseRecord(22, 1, false, 99));

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1, 20, 21, 22));

        assertThat(index.existing())
                .containsEntry(1, AutomationType.named("Automated"))
                .containsEntry(20, AutomationType.named("Manual"))
                .containsEntry(21, AutomationType.notSet())
                .containsEntry(22, AutomationType.unknown("99"));
    }

    @Test(timeOut = TIMEOUT_MS, description = "An exhausted budget fails with progress and leaves no running request")
    void budgetExhaustedFailsWithProgress() throws InterruptedException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 510)
                .delays(Duration.ofMillis(50), Duration.ofMillis(50), Duration.ofMillis(50));

        assertBudgetExhausted(source, ids(8, 60, 8));
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: the budget also bounds the case fields request")
    void budgetExhaustedDuringCaseFields() throws InterruptedException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).delays(SLOW, Duration.ZERO, Duration.ZERO);

        assertBudgetExhausted(source, Set.of(1, 2));
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: the budget also bounds tail point lookups")
    void budgetExhaustedDuringTail() throws InterruptedException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).delays(Duration.ZERO, SLOW, Duration.ZERO);

        assertBudgetExhausted(source, Set.of(1, 2, 3));
    }

    @Test(timeOut = TIMEOUT_MS, description = "HTTP 429 is reported as a rate limit")
    void rateLimitIsNamed() {
        TestRailApiException failure = new TestRailApiException("get_cases/7", 429);
        StubCaseSource source = new StubCaseSource().suite(1, 1, 300).failSuitePage(1, 250, failure);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, ids(1, 60, 1));

        assertThat(thrown).hasMessage("TestRail rate limit (429)").hasCause(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "HTTP 401 fails as rejected credentials, naming the variables only")
    void credentialsRejectedIsUnableToVerify() {
        TestRailApiException failure = new TestRailApiException("get_case/2", 401);
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCase(2, failure);
        IndexLoader loader = new IndexLoader(source, 2, BUDGET, THRESHOLD);

        UnableToVerifyError thrown =
                catchThrowableOfType(UnableToVerifyError.class, () -> loader.load(Set.of(1, 2, 3)));

        assertThat(thrown)
                .hasMessageStartingWith("TestRail rejected the credentials (HTTP 401)")
                .hasMessageContaining("TESTRAIL_USER")
                .hasMessageContaining("TESTRAIL_PASSWORD")
                .hasMessageContaining("project 7")
                .hasCause(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "HTTP 403 fails as rejected credentials")
    void forbiddenIsUnableToVerify() {
        TestRailApiException failure = new TestRailApiException("get_case/2", 403);
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCase(2, failure);
        IndexLoader loader = new IndexLoader(source, 2, BUDGET, THRESHOLD);

        assertThat(catchThrowableOfType(UnableToVerifyError.class, () -> loader.load(Set.of(1, 2, 3))))
                .hasMessageStartingWith("TestRail rejected the credentials (HTTP 403)");
    }

    @Test(timeOut = TIMEOUT_MS, description = "Network failures keep their message and cause")
    void networkFailureIsUnavailable() {
        UncheckedIOException failure = new UncheckedIOException(new IOException("connection reset"));
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCase(2, failure);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, Set.of(1, 2, 3));

        assertThat(thrown)
                .hasMessage("TestRail request failed: " + failure.getMessage())
                .hasCause(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "An unreadable answer is an unavailable service")
    void unreadableAnswerIsUnavailable() {
        IllegalStateException failure = new IllegalStateException("TestRail get_case/2 returned malformed JSON");
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCase(2, failure);

        assertThat(failLoad(source, 2, BUDGET, Set.of(1, 2, 3)))
                .hasMessage("TestRail request failed: TestRail get_case/2 returned malformed JSON")
                .hasCause(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A failure without a message is reported by its type")
    void failureWithoutMessageNamesType() {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCase(2, new IllegalStateException());

        assertThat(failLoad(source, 2, BUDGET, Set.of(1, 2, 3)))
                .hasMessage("TestRail request failed: java.lang.IllegalStateException");
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a suite whose pages never end is cut off")
    void endlessSuitePagesAreCutOff() {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 60).endlessPages(1);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, ids(1, 60, 1));

        assertThat(thrown)
                .hasMessage("TestRail suite 1 returned %d full pages without an end"
                        .formatted(IndexLoader.MAX_PAGES_PER_SUITE))
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThat(source.suitePageOffsets(1)).hasSizeLessThanOrEqualTo(IndexLoader.MAX_PAGES_PER_SUITE + 2);
    }

    @Test(timeOut = TIMEOUT_MS, description = "The first failure cancels outstanding requests without waiting for them")
    void firstFailureCancelsOutstandingRequests() throws InterruptedException {
        TestRailApiException failure = new TestRailApiException("get_cases/7", 500);
        StubCaseSource source = new StubCaseSource()
                .delays(Duration.ZERO, Duration.ZERO, SLOW)
                .failSuitePageOnceStarted(2, 0, failure, 1);
        Set<Integer> ids = interleavedSuites(source, 2, 30);
        long started = System.nanoTime();

        CaseServiceUnavailableException thrown = failLoad(source, 4, BUDGET, ids);

        assertThat(thrown)
                .hasMessage("TestRail request failed: TestRail get_cases/7 returned 500")
                .hasCause(failure);
        assertThat(source.suitePageSuites()).contains(1);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(SLOW);
        assertIdle(source);
    }

    @Test(
            timeOut = TIMEOUT_MS,
            description = "Without the automation type field cases still load, each with an unavailable type")
    void caseFieldsWithoutAutomationTypeGiveUnavailableTypes() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).withCaseFields(List.of());

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1, 2, 900));

        assertThat(index.missing()).containsExactly(900);
        assertThat(index.existing()).containsOnlyKeys(1, 2);
        assertThat(index.existing().values())
                .allSatisfy(type -> assertThat(type.toString())
                        .startsWith("unavailable (")
                        .contains("TestRail case field custom_automation_type not found")
                        .contains("TestRailCaseAutomatedRule"));
    }

    @Test(timeOut = TIMEOUT_MS, description = "A malformed automation type option gives unavailable types")
    void malformedAutomationTypeOptionGivesUnavailableTypes() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 10)
                .withCaseFields(List.of(new FieldRecord(CaseRecord.AUTOMATION_TYPE_FIELD, "x, Other")));

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1));

        assertThat(index.existing().get(1).toString()).contains("unreadable option 'x, Other'");
    }

    @Test(timeOut = TIMEOUT_MS, description = "An automation type option without a name gives unavailable types")
    void automationTypeOptionWithoutNameGivesUnavailableTypes() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 10)
                .withCaseFields(List.of(new FieldRecord(CaseRecord.AUTOMATION_TYPE_FIELD, "3")));

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1));

        assertThat(index.existing().get(1).toString()).startsWith("unavailable (");
    }

    @Test(timeOut = TIMEOUT_MS, description = "The automation type options are read from the configured field")
    void automationTypesComeFromTheConfiguredField() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 10)
                .withCaseFields(List.of(
                        new FieldRecord(CaseRecord.AUTOMATION_TYPE_FIELD, "2, Elsewhere"),
                        new FieldRecord("custom_kind", StubCaseSource.AUTOMATION_TYPES)));

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD, "custom_kind").load(Set.of(1));

        assertThat(index.existing()).containsEntry(1, AutomationType.named("Automated"));
    }

    @Test(timeOut = TIMEOUT_MS, description = "A defect in a worker is rethrown as is, not hidden as unavailable")
    void workerDefectIsRethrown() {
        NullPointerException failure = new NullPointerException("defect");
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCase(2, failure);
        IndexLoader loader = new IndexLoader(source, 2, BUDGET, THRESHOLD);

        assertThat(catchThrowableOfType(NullPointerException.class, () -> loader.load(Set.of(1, 2, 3))))
                .isSameAs(failure);
    }

    @Test(
            timeOut = TIMEOUT_MS,
            description = "An AssertionError from a worker is rethrown as is; the shared core reports it as unable to "
                    + "verify, never as a finding")
    void workerAssertionErrorIsRethrownForTheCore() {
        AssertionError failure = new AssertionError("probe");
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCaseWithError(2, failure);
        IndexLoader loader = new IndexLoader(source, 2, BUDGET, THRESHOLD);

        assertThat(catchThrowableOfType(AssertionError.class, () -> loader.load(Set.of(1, 2, 3))))
                .isSameAs(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A VirtualMachineError from a worker is rethrown as is")
    void outOfMemoryInWorkerIsRethrown() {
        OutOfMemoryError failure = new OutOfMemoryError("probe");
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).failFindCaseWithError(2, failure);
        IndexLoader loader = new IndexLoader(source, 2, BUDGET, THRESHOLD);

        assertThat(catchThrowableOfType(OutOfMemoryError.class, () -> loader.load(Set.of(1, 2, 3))))
                .isSameAs(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a probed case of another project is foreign")
    void foreignIdInProbeIsForeign() throws CaseServiceUnavailableException {
        StubCaseSource source = foreignFixture();

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(withForeign(HIGH_CASES));

        assertThat(source.findCaseIds()).hasSize(2).contains(FOREIGN_CASE);
        assertForeignOnly(source, index);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a tail case of another project is foreign")
    void foreignIdInTailIsForeign() throws CaseServiceUnavailableException {
        StubCaseSource source = foreignFixture();

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(withForeign(LOW_CASES));

        assertThat(source.findCaseIds()).hasSize(3).last().isEqualTo(FOREIGN_CASE);
        assertForeignOnly(source, index);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A foreign case is reported the same wherever its id sorts")
    void resultIndependentOfIdPosition() throws CaseServiceUnavailableException {
        CaseIndex probed = new IndexLoader(foreignFixture(), 2, BUDGET, THRESHOLD).load(withForeign(HIGH_CASES));
        CaseIndex tail = new IndexLoader(foreignFixture(), 2, BUDGET, THRESHOLD).load(withForeign(LOW_CASES));

        assertThat(probed.foreign()).isEqualTo(tail.foreign()).isEqualTo(Map.of(FOREIGN_CASE, FOREIGN_LABEL));
        assertThat(probed.missing()).isEqualTo(tail.missing()).isEmpty();
    }

    @Test(timeOut = TIMEOUT_MS, description = "A deleted case of another project is deleted, not foreign or missing")
    void deletedForeignCaseIsDeleted() throws CaseServiceUnavailableException {
        StubCaseSource source = foreignFixture().deleted(FOREIGN_CASE, FOREIGN_SUITE);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(withForeign(HIGH_CASES));

        assertThat(index.deleted()).containsExactly(FOREIGN_CASE);
        assertThat(index.missing()).isEmpty();
        assertThat(index.foreign()).isEmpty();
        assertThat(index.existing()).doesNotContainKey(FOREIGN_CASE);
        assertThat(source.suiteProjectCalls(FOREIGN_SUITE)).isZero();
    }

    @Test(timeOut = TIMEOUT_MS, description = "The project of a foreign suite is requested once for all its cases")
    void foreignSuiteResolvedOnce() throws CaseServiceUnavailableException {
        StubCaseSource source = foreignFixture().foreignSuite(FOREIGN_SUITE, FOREIGN_PROJECT, 1, 2_500_000);
        Set<Integer> ids = withForeign(HIGH_CASES);
        ids.addAll(Set.of(1, 2_500_000));

        CaseIndex index = new IndexLoader(source, 3, BUDGET, THRESHOLD).load(ids);

        assertThat(source.suiteProjectCalls(FOREIGN_SUITE)).isEqualTo(1);
        assertThat(index.foreign())
                .isEqualTo(Map.of(1, FOREIGN_LABEL, FOREIGN_CASE, FOREIGN_LABEL, 2_500_000, FOREIGN_LABEL));
        assertThat(index.missing()).isEmpty();
        assertThat(source.suitePageSuites()).doesNotContain(FOREIGN_SUITE);
        assertThat(source.calls())
                .isEqualTo(
                        2 + source.findCaseCalls() + source.suitePageOffsets().size() + 1);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A rate-limited foreign suite lookup makes the index unavailable")
    void foreignSuiteLookupFailureIsUnavailable() throws InterruptedException {
        TestRailApiException failure = new TestRailApiException("get_suite/77", 429);
        StubCaseSource source = foreignFixture().failSuiteProject(FOREIGN_SUITE, failure);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, withForeign(LOW_CASES));

        assertThat(thrown).hasMessage("TestRail rate limit (429)").hasCause(failure);
        assertIdle(source);
    }

    @Test(timeOut = TIMEOUT_MS, description = "A failed foreign suite lookup makes the index unavailable")
    void foreignSuiteLookupServerErrorIsUnavailable() {
        TestRailApiException failure = new TestRailApiException("get_suite/77", 500);
        StubCaseSource source = foreignFixture().failSuiteProject(FOREIGN_SUITE, failure);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, withForeign(HIGH_CASES));

        assertThat(thrown)
                .hasMessage("TestRail request failed: " + failure.getMessage())
                .hasCause(failure);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: the budget also bounds foreign suite lookups")
    void budgetExhaustedDuringForeignSuiteLookup() throws InterruptedException {
        StubCaseSource source = foreignFixture().suiteProjectDelay(SLOW);

        assertBudgetExhausted(source, withForeign(LOW_CASES));
    }

    @Test(timeOut = TIMEOUT_MS, description = "Without foreign ids the only extra request is the project suite list")
    void noForeignIdsAddsOnlyProjectSuitesRequest() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 300);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(ids(1, 60, 5));

        assertThat(index.foreign()).isEmpty();
        assertThat(source.projectSuitesCalls()).isEqualTo(1);
        assertThat(source.suiteProjectCalls()).isZero();
        int withoutSuites =
                1 + source.findCaseCalls() + source.suitePageOffsets().size();
        assertThat(source.calls()).isEqualTo(withoutSuites + 1).isEqualTo(7);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a project without suites is unavailable")
    void emptyProjectSuitesIsUnavailable() {
        StubCaseSource source = new StubCaseSource().foreignSuite(FOREIGN_SUITE, FOREIGN_PROJECT, FOREIGN_CASE);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, Set.of(FOREIGN_CASE));

        assertThat(thrown).hasMessage("TestRail project %d returned no suites".formatted(StubCaseSource.PROJECT_ID));
        assertThat(source.findCaseCalls()).isZero();
    }

    @Test(timeOut = TIMEOUT_MS, description = "Regression guard: a foreign suite of the own project is unavailable")
    void foreignSuiteOfOwnProjectIsUnavailable() {
        StubCaseSource source = new StubCaseSource()
                .suite(1, LOW_CASES, 60)
                .foreignSuite(FOREIGN_SUITE, StubCaseSource.PROJECT_ID, FOREIGN_CASE);

        CaseServiceUnavailableException thrown = failLoad(source, 2, BUDGET, withForeign(LOW_CASES));

        assertThat(thrown)
                .hasMessage("TestRail suite %d is not in project %d but belongs to it"
                        .formatted(FOREIGN_SUITE, StubCaseSource.PROJECT_ID));
    }

    @Test(timeOut = TIMEOUT_MS, description = "A case without a suite is unavailable")
    void caseWithoutSuiteIsUnavailable() {
        StubCaseSource source = new StubCaseSource().suite(1, 1, 10).withCase(new CaseRecord(900, null, false, 2));

        assertThat(failLoad(source, 2, BUDGET, Set.of(1, 900))).hasMessage("TestRail case 900 has no suite_id");
    }

    @Test(timeOut = TIMEOUT_MS, description = "A refused project suite list fails as rejected credentials")
    void projectSuitesRefusedIsUnableToVerify() throws InterruptedException {
        TestRailApiException failure = new TestRailApiException("get_suites/7", 401);
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 10)
                .failProjectSuites(failure)
                .delays(START_DELAY, Duration.ZERO, Duration.ZERO);
        IndexLoader loader = new IndexLoader(source, 2, BUDGET, THRESHOLD);

        assertThat(catchThrowableOfType(UnableToVerifyError.class, () -> loader.load(Set.of(1))))
                .hasMessageStartingWith("TestRail rejected the credentials (HTTP 401)")
                .hasCause(failure);
        assertIdle(source);
    }

    @Test(timeOut = TIMEOUT_MS, description = "Case fields and the project suite list are requested concurrently")
    void startRequestsRunTogether() throws CaseServiceUnavailableException {
        StubCaseSource source = new StubCaseSource()
                .suite(1, 1, 10)
                .delays(START_DELAY, Duration.ZERO, Duration.ZERO)
                .projectSuitesDelay(START_DELAY);

        CaseIndex index = new IndexLoader(source, 2, BUDGET, THRESHOLD).load(Set.of(1));

        assertThat(source.maxActive()).isEqualTo(2);
        assertThat(index.existing()).containsKey(1);
    }

    @Test(description = "Loader settings are validated")
    void rejectsInvalidSettings() {
        StubCaseSource source = new StubCaseSource();

        assertThat(catchThrowableOfType(
                        IllegalArgumentException.class, () -> new IndexLoader(source, 0, BUDGET, THRESHOLD)))
                .hasMessageContaining("parallelism");
        assertThat(catchThrowableOfType(IllegalArgumentException.class, () -> new IndexLoader(source, 1, BUDGET, -1)))
                .hasMessageContaining("lookupThreshold");
    }

    private static StubCaseSource foreignFixture() {
        return new StubCaseSource()
                .suite(1, LOW_CASES, 60)
                .suite(2, HIGH_CASES, 60)
                .foreignSuite(FOREIGN_SUITE, FOREIGN_PROJECT, FOREIGN_CASE);
    }

    private static Set<Integer> withForeign(int firstCase) {
        Set<Integer> ids = ids(firstCase, 60, 1);
        ids.add(FOREIGN_CASE);
        return ids;
    }

    private static void assertForeignOnly(StubCaseSource source, CaseIndex index) {
        assertThat(index.foreign()).isEqualTo(Map.of(FOREIGN_CASE, FOREIGN_LABEL));
        assertThat(index.existing()).doesNotContainKey(FOREIGN_CASE);
        assertThat(index.missing()).isEmpty();
        assertThat(source.suitePageSuites()).doesNotContain(FOREIGN_SUITE);
        assertThat(source.suiteProjectCalls(FOREIGN_SUITE)).isEqualTo(1);
    }

    private static void assertBudgetExhausted(StubCaseSource source, Set<Integer> ids) throws InterruptedException {
        CaseServiceUnavailableException thrown = failLoad(source, 2, SHORT_BUDGET, ids);

        assertThat(thrown.getMessage()).matches(PROGRESS);
        assertThat(thrown.getCause()).isInstanceOf(TimeoutException.class).hasMessage(thrown.getMessage());
        assertIdle(source);
        assertWorkersStopped(source);
    }

    /** The loader's own pool threads, the ones that called the source, all end once the load has failed. */
    private static void assertWorkersStopped(StubCaseSource source) throws InterruptedException {
        assertThat(source.workerThreads()).isNotEmpty();
        for (Thread worker : source.workerThreads()) {
            worker.join(IDLE_WAIT.toMillis());
            assertThat(worker.getName()).startsWith("testrail-index-");
            assertThat(worker.isAlive())
                    .as("%s still running", worker.getName())
                    .isFalse();
        }
    }

    private static CaseServiceUnavailableException failLoad(
            StubCaseSource source, int parallelism, Duration budget, Set<Integer> ids) {
        IndexLoader loader = new IndexLoader(source, parallelism, budget, THRESHOLD);
        return catchThrowableOfType(CaseServiceUnavailableException.class, () -> loader.load(ids));
    }

    private static void assertIdle(StubCaseSource source) throws InterruptedException {
        long deadline = System.nanoTime() + IDLE_WAIT.toNanos();
        while (source.active() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(source.active()).isZero();
    }

    private static void assertPagedUpTo(StubCaseSource source, int suiteId, int lastOffset, int parallelism) {
        List<Integer> offsets = source.suitePageOffsets(suiteId);
        List<Integer> needed = IntStream.rangeClosed(0, lastOffset / CaseSource.PAGE_SIZE)
                .mapToObj(page -> page * CaseSource.PAGE_SIZE)
                .toList();
        assertThat(offsets)
                .doesNotHaveDuplicates()
                .containsAll(needed)
                .hasSizeLessThanOrEqualTo(needed.size() + parallelism - 1);
    }

    private static Set<Integer> contiguousSuites(StubCaseSource source, int suites, int casesPerSuite) {
        Set<Integer> all = new TreeSet<>();
        for (int suite = 1; suite <= suites; suite++) {
            List<Integer> caseIds = ids(SUITES_FIRST_ID + (suite - 1) * casesPerSuite, casesPerSuite, 1).stream()
                    .toList();
            source.suite(suite, caseIds);
            all.addAll(caseIds);
        }
        return all;
    }

    private static Set<Integer> interleavedSuites(StubCaseSource source, int suites, int casesPerSuite) {
        Set<Integer> all = new TreeSet<>();
        for (int suite = 1; suite <= suites; suite++) {
            List<Integer> caseIds = ids(SUITES_FIRST_ID + suite - 1, casesPerSuite, suites).stream()
                    .toList();
            source.suite(suite, caseIds);
            all.addAll(caseIds);
        }
        return all;
    }

    private static Set<Integer> ids(int first, int count, int step) {
        return IntStream.range(0, count)
                .map(index -> first + index * step)
                .boxed()
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
