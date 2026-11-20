package dev.quokkify.archgate.rules.archunit.testplanit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.cases.archunit.AutomationType;
import dev.quokkify.archgate.cases.archunit.CaseIndex;
import dev.quokkify.archgate.cases.archunit.CaseQuery;
import dev.quokkify.archgate.cases.archunit.CaseServiceUnavailableException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.testng.annotations.Test;

/** The load itself: chunking, classification and the time limits. */
class CaseLookupTest {

    @Test(description = "Ids are asked for in chunks of 200, and every id is classified")
    void asksInChunksAndClassifiesEveryId() throws CaseServiceUnavailableException {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            SortedSet<Integer> ids = new TreeSet<>();
            IntStream.rangeClosed(1, 450).forEach(ids::add);
            ids.add(1234567);

            CaseIndex index = new TestPlanItCaseService().load(new CaseQuery(ids, false), testPlanIt.variables());

            assertThat(testPlanIt.requests()).hasSize(4);
            assertThat(testPlanIt.requests().get(0)).startsWith("projects/findUnique ");
            assertThat(testPlanIt.requests().subList(1, 4))
                    .allSatisfy(request -> assertThat(request).startsWith("repositoryCases/findMany "))
                    .satisfiesExactly(
                            first ->
                                    assertThat(first).contains("\"in\":[1,2,3,").contains(",200]"),
                            second ->
                                    assertThat(second).contains("\"in\":[201,").contains(",400]"),
                            third -> assertThat(third).contains("\"in\":[401,").contains(",450,1234567]"));
            assertThat(index.existing())
                    .isEqualTo(Map.of(1, AutomationType.named("Automated"), 2, AutomationType.named("Manual")));
            assertThat(index.deleted()).isEqualTo(Set.of(10));
            assertThat(index.foreign()).isEqualTo(Map.of(1234567, "3"));
            assertThat(index.missing()).hasSize(447).doesNotContain(1, 2, 10, 1234567);
        }
    }

    @Test(description = "A response slower than the read timeout gives up and is unavailable")
    void slowAnswerTimesOut() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            testPlanIt.delay(Duration.ofSeconds(5));
            TestPlanItConnection connection = connection(testPlanIt, Duration.ofMillis(200), Duration.ofSeconds(30));

            long started = System.nanoTime();
            assertThatThrownBy(() -> load(connection, Set.of(1)))
                    .isInstanceOf(CaseServiceUnavailableException.class)
                    .hasMessageContaining("timed out after 200 ms");
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(4));
        }
    }

    @Test(description = "A load that needs more time than its budget stops and is unavailable")
    void budgetBoundsTheWholeLoad() {
        try (FakeTestPlanIt testPlanIt = new FakeTestPlanIt()) {
            testPlanIt.delay(Duration.ofMillis(150));
            TestPlanItConnection connection = new TestPlanItConnection(
                    testPlanIt.url(),
                    FakeTestPlanIt.TOKEN,
                    7,
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(400),
                    1);

            assertThatThrownBy(() -> load(connection, Set.of(1, 2, 3, 4, 5, 6)))
                    .isInstanceOf(CaseServiceUnavailableException.class)
                    .hasMessageContaining("budget of 400 ms");
            assertThat(testPlanIt.requests().size()).isLessThan(7);
        }
    }

    @Test(description = "The connection never prints the token")
    void connectionHidesTheToken() {
        TestPlanItConnection connection = TestPlanItConnection.of("https://tms.example.org", FakeTestPlanIt.TOKEN, 7);

        assertThat(connection.toString()).contains("token=***").doesNotContain(FakeTestPlanIt.TOKEN);
    }

    @Test(description = "The connection rejects unusable values")
    void connectionRejectsInvalidValues() {
        assertThatThrownBy(() -> TestPlanItConnection.of("tms.example.org", "t", 7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TestPlanItConnection.of("https://tms.example.org", "t", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestPlanItConnection(
                        "https://tms.example.org",
                        "t",
                        7,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestPlanItConnection(
                        "https://tms.example.org",
                        "t",
                        7,
                        Duration.ZERO,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static TestPlanItConnection connection(FakeTestPlanIt testPlanIt, Duration read, Duration budget) {
        return new TestPlanItConnection(
                testPlanIt.url(), FakeTestPlanIt.TOKEN, 7, Duration.ofSeconds(1), read, budget, 200);
    }

    private static CaseIndex load(TestPlanItConnection connection, Set<Integer> ids)
            throws CaseServiceUnavailableException {
        try (TestPlanItClient client = new TestPlanItClient(connection)) {
            return new CaseLookup(client, connection).load(new TreeSet<>(ids));
        }
    }
}
