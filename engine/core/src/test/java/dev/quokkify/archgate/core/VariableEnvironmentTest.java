package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.annotations.Test;

class VariableEnvironmentTest {

    private static VariableEnvironment env(boolean offline, String... keyValues) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put(keyValues[i], keyValues[i + 1]);
        }
        return new VariableEnvironment(values::get, offline);
    }

    @Test
    void requireReturnsValuesInOrder() {
        Map<String, String> values = env(false, "B", "2", "A", "1").require("B", "A");

        assertThat(values).containsExactly(Map.entry("B", "2"), Map.entry("A", "1"));
    }

    @Test
    void requireListsEveryMissingAndBlankName() {
        VariableEnvironment environment = env(false, "A", " ", "C", "3");

        assertThatThrownBy(() -> environment.require("A", "B", "C"))
                .isInstanceOfSatisfying(ExternalDependencyUnavailable.class, error -> {
                    assertThat(error.reason()).isEqualTo(ExternalDependencyUnavailable.Reason.MISSING_VARIABLES);
                    assertThat(error.missingVariables()).isEqualTo(List.of("A", "B"));
                    assertThat(error.getMessage()).startsWith("Missing A, B.");
                });
    }

    @Test
    void requireRejectsNullOrBlankNames() {
        VariableEnvironment environment = env(false);

        assertThatThrownBy(() -> environment.require("A", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variable names must not be null or blank");
        assertThatThrownBy(() -> environment.require((String) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variable names must not be null or blank");
        assertThatThrownBy(() -> environment.optional(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Variable names must not be null or blank");
    }

    @Test
    void offlineSkipsBeforeLookup() {
        AtomicInteger lookups = new AtomicInteger();
        VariableEnvironment environment = new VariableEnvironment(
                name -> {
                    lookups.incrementAndGet();
                    return "value";
                },
                true);

        assertThatThrownBy(() -> environment.require("A"))
                .isInstanceOfSatisfying(
                        ExternalDependencyUnavailable.class,
                        error -> assertThat(error.reason()).isEqualTo(ExternalDependencyUnavailable.Reason.OFFLINE));
        assertThat(lookups).hasValue(0);
    }

    @Test
    void optionalIgnoresOffline() {
        assertThat(env(true, "A", "1").optional("A")).contains("1");
    }

    @Test
    void optionalBlankIsEmpty() {
        VariableEnvironment environment = env(false, "A", "  ");

        assertThat(environment.optional("A")).isEmpty();
        assertThat(environment.optional("B")).isEmpty();
    }

    @Test
    void wasReadAfterRequireEvenWhenItThrows() {
        VariableEnvironment untouched = env(false);
        VariableEnvironment required = env(false, "A", "1");
        VariableEnvironment failed = env(false);
        VariableEnvironment optionalRead = env(false);
        VariableEnvironment offline = env(true);

        required.require("A");
        assertThatThrownBy(() -> failed.require("A")).isInstanceOf(ExternalDependencyUnavailable.class);
        optionalRead.optional("A");
        assertThatThrownBy(() -> offline.require("A")).isInstanceOf(ExternalDependencyUnavailable.class);

        assertThat(untouched.wasRead()).isFalse();
        assertThat(required.wasRead()).isTrue();
        assertThat(failed.wasRead()).isTrue();
        assertThat(optionalRead.wasRead()).isTrue();
        assertThat(offline.wasRead()).isTrue();
    }
}
