package dev.quokkify.archgate.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.logging.log4j.ThreadContext;
import org.testng.annotations.Test;

class ScenarioLogTest {

    @Test
    void missingReportAttachesNothing() {
        assertThatCode(() -> ScenarioLog.report(BuildTool.GRADLE, Path.of("build", "no-such-report.txt")))
                .doesNotThrowAnyException();
    }

    @Test
    void writesOutputToPerRunFile() throws IOException {
        ScenarioLog.run("log-check", BuildTool.MAVEN, 2, () -> {
            ScenarioLog.output("hello-output");
            return null;
        });

        assertThat(ScenarioLog.logFile("log-check", BuildTool.MAVEN, 2))
                .isRegularFile()
                .content()
                .contains("hello-output");
        assertThat(ThreadContext.get("scenario")).isNull();
        assertThat(ThreadContext.get("tool")).isNull();
        assertThat(ThreadContext.get("run")).isNull();
    }

    @Test
    void clearsMdcWhenActionThrows() {
        assertThatThrownBy(() -> ScenarioLog.run("log-check", BuildTool.GRADLE, 1, () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(ThreadContext.get("scenario")).isNull();
        assertThat(ThreadContext.get("tool")).isNull();
        assertThat(ThreadContext.get("run")).isNull();
    }
}
