package dev.quokkify.archgate.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FailureMessageTest {

    @Test
    void describeKeepsMessageWithoutCause() {
        assertThat(FailureMessage.describe(new UnableToVerifyError("not verified")))
                .isEqualTo("not verified");
    }

    @Test
    void describeAppendsCauseTypeAndMessage() {
        UnableToVerifyError error = new UnableToVerifyError("not verified", new IOException("disk gone"));

        assertThat(FailureMessage.describe(error))
                .startsWith("not verified")
                .endsWith("Caused by: java.io.IOException: disk gone");
    }

    @Test
    void describeAppendsCauseTypeWhenCauseHasNoMessage() {
        UnableToVerifyError error = new UnableToVerifyError("not verified", new IllegalStateException());

        assertThat(FailureMessage.describe(error)).endsWith("Caused by: java.lang.IllegalStateException");
    }
}
