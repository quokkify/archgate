package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FailOnTest {

    @Test
    void parsesCaseInsensitiveAndTrimmed() {
        assertThat(FailOn.parse(" error ")).contains(Severity.ERROR);
        assertThat(FailOn.parse("Info")).contains(Severity.INFO);
        assertThat(FailOn.parse("never")).isEqualTo(Optional.empty());
    }

    @Test
    void defaultIsWarning() {
        assertThat(FailOn.DEFAULT).isEqualTo(Severity.WARNING);
    }

    @Test
    void unknownValueIsUnableToVerify() {
        assertThatThrownBy(() -> FailOn.parse("bogus"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("INFO, WARNING, ERROR or NEVER");
    }

    @Test
    void nullValueIsUnableToVerify() {
        assertThatThrownBy(() -> FailOn.parse(null)).isInstanceOf(UnableToVerifyError.class);
    }
}
