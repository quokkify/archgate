package dev.quokkify.archgate.rules.archunit.testrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class TestRailConnectionTest {

    private static final String URL = "https://tms.example.org";

    @Test
    void appliesDefaults() {
        TestRailConnection connection = TestRailConnection.of(URL, "reader", "key", 3);

        assertThat(connection.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(connection.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(connection.parallelism()).isEqualTo(8);
        assertThat(connection.budget()).isEqualTo(Duration.ofSeconds(60));
        assertThat(connection.lookupThreshold()).isEqualTo(50);
    }

    @Test
    void neverPrintsThePassword() {
        String text = TestRailConnection.of(URL, "reader", "key-123", 3).toString();

        assertThat(text).doesNotContain("key-123").contains("password=***").contains("user=reader");
    }

    @DataProvider
    Object[][] invalid() {
        Duration second = Duration.ofSeconds(1);
        return new Object[][] {
            {URL, 0, second, second, 1, second, 0, "projectId must be positive: 0"},
            {URL, 1, second, second, 0, second, 0, "parallelism must be positive: 0"},
            {URL, 1, second, second, 1, second, -1, "lookupThreshold must not be negative: -1"},
            {URL, 1, Duration.ofNanos(10), second, 1, second, 0, "connectTimeout must be at least 1 ms: PT0.00000001S"},
            {URL, 1, second, Duration.ZERO, 1, second, 0, "readTimeout must be at least 1 ms: PT0S"},
            {URL, 1, second, second, 1, Duration.ofSeconds(-1), 0, "budget must be positive: PT-1S"},
            {"tms.example.org", 1, second, second, 1, second, 0, "baseUrl must be an absolute http or https URL"},
            {"ftp://tms.example.org", 1, second, second, 1, second, 0, "baseUrl must be an absolute http or https URL"},
            {"https://u:p@tms.example.org", 1, second, second, 1, second, 0, "baseUrl must not contain user info"},
            {
                "https://tms.example.org?x=1",
                1,
                second,
                second,
                1,
                second,
                0,
                "baseUrl must not contain a query or fragment"
            },
        };
    }

    @Test(dataProvider = "invalid")
    void rejectsInvalidValues(
            String url,
            int projectId,
            Duration connect,
            Duration read,
            int parallelism,
            Duration budget,
            int threshold,
            String message) {
        assertThatThrownBy(() -> new TestRailConnection(
                        url, "reader", "key", projectId, connect, read, parallelism, budget, threshold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message)
                .hasMessageNotContaining("key");
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> TestRailConnection.of(null, "reader", "key", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TestRailConnection.of(URL, null, "key", 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TestRailConnection.of(URL, "reader", null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
