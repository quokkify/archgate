package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads {@code BLOCK} and puts a regular file there, so a report file below it cannot be written. */
public class ReportBlockingLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "report-blocking-launch-rule";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Class<LaunchContext> contextType() {
        return LaunchContext.class;
    }

    @Override
    public void verify(LaunchContext context) {
        Path blocker = Path.of(context.environment().optional("BLOCK").orElseThrow());
        try {
            Files.writeString(blocker, "not a directory");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        checkViolations("no violations", List.of());
    }
}
