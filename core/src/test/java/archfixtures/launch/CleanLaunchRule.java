package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import java.util.List;

public class CleanLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "clean-launch-rule";
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
        checkViolations("no violations", List.of());
    }
}
