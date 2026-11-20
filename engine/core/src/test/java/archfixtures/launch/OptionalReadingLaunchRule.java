package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import java.util.List;

public class OptionalReadingLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "optional-reading-launch-rule";
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
        context.environment().optional("x");
        checkViolations("no violations", List.of());
    }
}
