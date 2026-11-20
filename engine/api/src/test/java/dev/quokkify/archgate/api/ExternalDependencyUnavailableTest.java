package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.testng.annotations.Test;

class ExternalDependencyUnavailableTest {

    @Test
    void missingVariablesMessageListsAllNames() {
        ExternalDependencyUnavailable error = ExternalDependencyUnavailable.missingVariables(List.of("A", "B"));

        assertThat(error.getMessage())
                .isEqualTo("Missing A, B. Pass them as -DNAME=value or as environment variables.");
        assertThat(error.reason()).isEqualTo(ExternalDependencyUnavailable.Reason.MISSING_VARIABLES);
        assertThat(error.missingVariables()).containsExactly("A", "B");
        assertThat(error.service()).isEmpty();
    }

    @Test
    void missingVariablesRejectsEmpty() {
        assertThatThrownBy(() -> ExternalDependencyUnavailable.missingVariables(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void offlineMessage() {
        ExternalDependencyUnavailable error = ExternalDependencyUnavailable.offline();

        assertThat(error.getMessage()).isEqualTo("External rules are switched off by archgate.offline=true.");
        assertThat(error.reason()).isEqualTo(ExternalDependencyUnavailable.Reason.OFFLINE);
        assertThat(error.missingVariables()).isEmpty();
    }

    @Test
    void serviceUnavailableUsesCauseMessageOrClass() {
        IOException withMessage = new IOException("Connect timed out");
        ExternalDependencyUnavailable first = ExternalDependencyUnavailable.serviceUnavailable("Tracker", withMessage);
        IOException bare = new IOException();
        ExternalDependencyUnavailable second = ExternalDependencyUnavailable.serviceUnavailable("Tracker", bare);

        assertThat(first.getMessage()).isEqualTo("Tracker is unavailable: Connect timed out");
        assertThat(first.getCause()).isSameAs(withMessage);
        assertThat(first.service()).contains("Tracker");
        assertThat(first.reason()).isEqualTo(ExternalDependencyUnavailable.Reason.SERVICE_UNAVAILABLE);
        assertThat(second.getMessage()).isEqualTo("Tracker is unavailable: java.io.IOException");
        assertThat(second.getCause()).isSameAs(bare);
    }
}
