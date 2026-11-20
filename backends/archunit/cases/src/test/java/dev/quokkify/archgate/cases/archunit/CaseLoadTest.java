package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable.Reason;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;

public class CaseLoadTest {

    private static final JavaClasses LINKED = new ClassFileImporter().importPackages("archfixtures.linkedcases");
    private static final JavaClasses RULE_FIXTURES = new ClassFileImporter().importPackages("archfixtures.caserules");
    private static final JavaClasses UNLINKED = new ClassFileImporter().importClasses(String.class);
    private static final String EXISTING_FILE = "module/src/test/java/archfixtures/caserules/ExistingCasesTest.java";
    private static final Map<String, String> CREDENTIALS = StubCaseService.CREDENTIALS;
    private static final int THREADS = 8;
    private static final long WAIT_SECONDS = 30;

    @Test(description = "Regression guard: without a linked test nothing is loaded and no variable is needed")
    public void noLinkedTestMeansNoLoadAndNoVariables() {
        StubCaseService service = StubCaseService.allExisting();
        FixtureContext context = new FixtureContext(UNLINKED, Map.of());

        CaseLoad.Loaded loaded = CaseLoad.load(context, service);

        assertThat(loaded.linked().isEmpty()).isTrue();
        assertThat(loaded.index()).isEqualTo(CaseIndex.empty());
        assertThat(service.loads()).isZero();
        assertThat(context.sharedNames()).isEmpty();
        assertThat(context.testCalls()).isEqualTo(1);
    }

    @Test(description = "Without a linked test the rules pass even when external rules are switched off")
    public void noLinkedTestPassesOffline() {
        StubCaseService service = StubCaseService.allExisting();

        CaseLoad.Loaded loaded = CaseLoad.load(new FixtureContext(UNLINKED, Map.of(), true), service);

        assertThat(loaded.linked().isEmpty()).isTrue();
        assertThat(service.loads()).isZero();
    }

    @Test(description = "Missing variables are reported together, after one scan, when some test links a case")
    public void missingVariablesWithLinksAreUnavailable() {
        StubCaseService service = StubCaseService.allExisting();
        FixtureContext context = new FixtureContext(LINKED, Map.of());

        assertThatThrownBy(() -> CaseLoad.load(context, service))
                .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.MISSING_VARIABLES);
                    assertThat(e.missingVariables()).containsExactly("TRACKER_USER", "TRACKER_TOKEN");
                });
        assertThat(service.loads()).isZero();
        assertThat(context.testCalls()).isEqualTo(1);
    }

    @Test(description = "External rules switched off skip a rule whose tests link cases")
    public void offlineWithLinksIsUnavailable() {
        StubCaseService service = StubCaseService.allExisting();

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS, true), service))
                .isInstanceOfSatisfying(
                        ExternalDependencyUnavailable.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.OFFLINE));
        assertThat(service.loads()).isZero();
    }

    @Test(description = "A service that needs no variables still honours switched-off external rules")
    public void serviceWithoutVariablesStillHonoursOffline() {
        StubCaseService service = StubCaseService.allExisting().withVariables(List.of());

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, Map.of(), true), service))
                .isInstanceOfSatisfying(
                        ExternalDependencyUnavailable.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.OFFLINE));

        CaseLoad.load(new FixtureContext(LINKED, Map.of()), service);
        assertThat(service.variables()).containsExactly(Map.of());
    }

    @Test(description = "The service gets the linked ids, sorted, and the values of its variables")
    public void serviceGetsLinkedIdsAndVariables() {
        StubCaseService service = StubCaseService.allExisting();

        CaseLoad.Loaded loaded = CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS), service);

        assertThat(service.queries()).singleElement().satisfies(query -> {
            assertThat(query.ids()).containsExactly(12, 13, 14);
            assertThat(query.changedFilesOnly()).isFalse();
        });
        assertThat(service.variables()).containsExactly(CREDENTIALS);
        assertThat(loaded.linked().ids()).containsExactly(12, 13, 14);
    }

    @Test(description = "Two rules on one context load the index once and get the same object")
    public void sameInputsShareOneLoad() {
        StubCaseService service = StubCaseService.allExisting();
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);

        CaseIndex first = CaseLoad.load(context, service).index();

        assertThat(CaseLoad.load(context, service).index()).isSameAs(first);
        assertThat(service.loads()).isEqualTo(1);
        assertThat(context.sharedNames()).containsExactly("Tracker index", "Tracker index");
    }

    @Test(description = "Parallel callers share one load")
    public void concurrentCallsLoadOnce() throws Exception {
        StubCaseService service = StubCaseService.allExisting().delayed(Duration.ofMillis(200));
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<CaseIndex>> results = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Callable<CaseIndex> call = () -> {
                    assertThat(start.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                    return CaseLoad.load(context, service).index();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            CaseIndex first = results.get(0).get(WAIT_SECONDS, TimeUnit.SECONDS);
            for (Future<CaseIndex> result : results) {
                assertThat(result.get(WAIT_SECONDS, TimeUnit.SECONDS)).isSameAs(first);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(service.loads()).isEqualTo(1);
    }

    @Test(description = "An unavailable service becomes the skip signal, is cached and keeps its cause")
    public void unavailableServiceIsCachedSkipSignal() {
        StubCaseService service = StubCaseService.allExisting().failingUnavailable("tracker is down");
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> CaseLoad.load(context, service))
                    .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, e -> {
                        assertThat(e.reason()).isEqualTo(Reason.SERVICE_UNAVAILABLE);
                        assertThat(e.service()).hasValue("Tracker");
                        assertThat(fullMessage(e)).contains("tracker is down");
                    });
        }
        assertThat(service.loads()).isEqualTo(1);
    }

    @Test(description = "Any other failure of the service is unable-to-verify, naming the index and the cause")
    public void runtimeFailureIsUnableToVerify() {
        StubCaseService service = StubCaseService.allExisting().failing(new IllegalStateException("bad answer"));

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS), service))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("Tracker index could not be built: ")
                .hasMessageContaining("bad answer");
    }

    @Test(description = "Regression guard: an AssertionError from the service is unable-to-verify, not a finding")
    public void assertionErrorIsUnableToVerify() {
        AssertionError defect = new AssertionError("expected response time");
        StubCaseService service = StubCaseService.allExisting().failingWith(defect);

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS), service))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("Tracker index could not be built: ")
                .hasMessageContaining("expected response time")
                .hasCause(defect);
    }

    @Test(description = "Other errors of the service, such as running out of memory, propagate unchanged")
    public void otherErrorPropagates() {
        OutOfMemoryError exhausted = new OutOfMemoryError("probe");
        StubCaseService service = StubCaseService.allExisting().failingWith(exhausted);

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS), service))
                .isSameAs(exhausted);
    }

    @Test(description = "Regression guard: a service that returns no index fails closed")
    public void nullIndexIsUnableToVerify() {
        StubCaseService service = StubCaseService.answering(query -> null);

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS), service))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("Tracker index could not be built")
                .hasMessageContaining("no index");
    }

    @Test(description = "Regression guard: a linked id the index does not classify fails closed instead of passing")
    public void unclassifiedIdIsUnableToVerify() {
        StubCaseService service = StubCaseService.fixed(
                new CaseIndex(Map.of(12, AutomationType.notSet()), Set.of(13), Set.of(), Map.of()));

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, CREDENTIALS), service))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("Tracker index could not be built")
                .hasMessageContaining("C14");
    }

    @Test(description = "A shared index built for other inputs is not handed out silently, and no value is printed")
    public void sharedIndexForOtherInputsIsUnableToVerify() {
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);
        CaseLoad.load(context, StubCaseService.allExisting());
        StubCaseService narrower = StubCaseService.allExisting().withVariables(List.of("TRACKER_USER"));

        assertThatThrownBy(() -> CaseLoad.load(context, narrower))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("Tracker index was built for other inputs")
                .hasMessageContaining("TRACKER_TOKEN")
                .hasMessageContaining("must read the same test classes and variables")
                .hasMessageNotContaining("token");
    }

    @Test(description = "Regression guard: two configurations of one service class do not share an index")
    public void otherServiceConfigurationIsKeyMismatch() {
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);
        CaseLoad.load(context, StubCaseService.allExisting().configured(new Setting("alpha-secret")));
        StubCaseService other = StubCaseService.allExisting().configured(new Setting("beta-secret"));

        assertThatThrownBy(() -> CaseLoad.load(context, other))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("Tracker index was built for other inputs")
                .hasMessageContaining("differing service configuration")
                .hasMessageNotContaining("alpha-secret")
                .hasMessageNotContaining("beta-secret")
                .hasMessageNotContaining("token");
        assertThat(other.loads()).isZero();
    }

    @Test(description = "Equal configurations of one service class share a single load")
    public void equalServiceConfigurationsShareOneLoad() {
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);
        StubCaseService first = StubCaseService.allExisting().configured(new Setting("same"));
        StubCaseService second = StubCaseService.allExisting().configured(new Setting("same"));

        CaseIndex index = CaseLoad.load(context, first).index();

        assertThat(CaseLoad.load(context, second).index()).isSameAs(index);
        assertThat(first.loads() + second.loads()).isEqualTo(1);
    }

    @Test(description = "By default a service is identified by its class")
    public void defaultShareKeyIsTheClass() {
        assertThat(StubCaseService.allExisting().shareKey()).isEqualTo(StubCaseService.class);
    }

    @Test(description = "A service without a share key fails closed")
    public void nullShareKeyIsRejected() {
        CaseService noKey = new CaseService() {
            @Override
            public String displayName() {
                return StubCaseService.NAME;
            }

            @Override
            public List<String> requiredVariables() {
                return List.of();
            }

            @Override
            public CaseIndex load(CaseQuery query, Map<String, String> variables) {
                return CaseIndex.empty();
            }

            @Override
            public Object shareKey() {
                return null;
            }
        };

        assertThatThrownBy(() -> CaseLoad.load(new FixtureContext(LINKED, Map.of()), noKey))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("shareKey");
    }

    @Test(description = "A different service under the same display name is a key mismatch")
    public void otherServiceClassIsKeyMismatch() {
        FixtureContext context = new FixtureContext(LINKED, CREDENTIALS);
        CaseLoad.load(context, StubCaseService.allExisting());
        CaseService other = new CaseService() {
            @Override
            public String displayName() {
                return StubCaseService.NAME;
            }

            @Override
            public List<String> requiredVariables() {
                return StubCaseService.VARIABLES;
            }

            @Override
            public CaseIndex load(CaseQuery query, Map<String, String> variables) {
                return CaseIndex.empty();
            }
        };

        assertThatThrownBy(() -> CaseLoad.load(context, other))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageStartingWith("Tracker index was built for other inputs");
    }

    @Test(description = "Changed-files mode asks only for the ids of changed test classes")
    public void changedFilesModeLoadsOnlyChangedIds() throws IOException {
        StubCaseService service = StubCaseService.allExisting();
        FixtureContext context = changedContext(EXISTING_FILE, CREDENTIALS);

        CaseLoad.Loaded loaded = CaseLoad.load(context, service);

        assertThat(loaded.linked().ids()).containsExactly(1);
        assertThat(service.queries()).singleElement().satisfies(query -> {
            assertThat(query.ids()).containsExactly(1);
            assertThat(query.changedFilesOnly()).isTrue();
        });
    }

    @Test(description = "Changed-files mode with no changed test loads nothing and needs no variables")
    public void changedFilesModeWithoutChangedTestLoadsNothing() throws IOException {
        StubCaseService service = StubCaseService.allExisting();
        FixtureContext context = changedContext("", Map.of());

        CaseLoad.Loaded loaded = CaseLoad.load(context, service);

        assertThat(loaded.linked().isEmpty()).isTrue();
        assertThat(service.loads()).isZero();
        assertThat(context.sharedNames()).isEmpty();
    }

    @Test(description = "The shared load is named after the mode")
    public void sharedNameFollowsTheMode() throws IOException {
        FixtureContext changed = changedContext(EXISTING_FILE, CREDENTIALS);
        FixtureContext full = new FixtureContext(RULE_FIXTURES, CREDENTIALS);

        CaseLoad.load(changed, StubCaseService.allExisting());
        CaseLoad.load(full, StubCaseService.allExisting());

        assertThat(changed.sharedNames()).containsOnly("Tracker index (changed files)");
        assertThat(full.sharedNames()).containsOnly("Tracker index");
    }

    @Test(description = "Both rules in changed-files mode share one narrowed load")
    public void bothRulesShareOneNarrowedLoad() throws IOException {
        StubCaseService service = StubCaseService.allExisting();
        FixtureContext context = changedContext(EXISTING_FILE, CREDENTIALS);

        CaseLoad.load(context, service);
        CaseLoad.load(context, service);

        assertThat(service.loads()).isEqualTo(1);
    }

    @Test(description = "Without the variable every linked test class is checked")
    public void unsetVariableKeepsFullCheck() {
        CaseLoad.Loaded loaded =
                CaseLoad.load(new FixtureContext(RULE_FIXTURES, CREDENTIALS), StubCaseService.allExisting());

        assertThat(loaded.linked().ids())
                .containsExactlyElementsOf(LinkedCases.from(RULE_FIXTURES).ids());
        assertThat(loaded.linked().changedFilesOnly()).isFalse();
    }

    private static FixtureContext changedContext(String listContent, Map<String, String> variables) throws IOException {
        Path list = Files.createTempFile("changed-files", ".txt");
        list.toFile().deleteOnExit();
        Files.writeString(list, listContent, StandardCharsets.UTF_8);
        Map<String, String> all = new HashMap<>(variables);
        all.put(ChangedFiles.VARIABLE, list.toString());
        return new FixtureContext(RULE_FIXTURES, all);
    }

    private static String fullMessage(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = error; Objects.nonNull(current); current = current.getCause()) {
            text.append(current.getMessage()).append(' ');
        }
        return text.toString();
    }

    private record Setting(String value) {}
}
