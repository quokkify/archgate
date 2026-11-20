package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.testng.annotations.Test;

class EnvironmentTest {

    private static final Environment REQUIRE_ONLY = new Environment() {
        @Override
        public Map<String, String> require(String... names) {
            return Map.of();
        }
    };

    @Test
    void defaultOptionalIsUnableToVerify() {
        assertThatThrownBy(() -> REQUIRE_ONLY.optional("X"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage(
                        "This Environment provides no optional variables, so rules that read them cannot run with it.");
    }

    @Test
    void defaultUnavailableIsServiceUnavailable() {
        Throwable cause = new RuntimeException("down");
        ExternalDependencyUnavailable result = REQUIRE_ONLY.unavailable("Tracker", cause);

        assertThat(result.reason()).isEqualTo(ExternalDependencyUnavailable.Reason.SERVICE_UNAVAILABLE);
        assertThat(result).hasMessage("Tracker is unavailable: down").hasCause(cause);
    }
}
