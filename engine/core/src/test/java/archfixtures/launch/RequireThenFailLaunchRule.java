package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public class RequireThenFailLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "require-then-fail-launch-rule";
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
        context.environment().require("X");
        throw new IllegalStateException("unrelated failure after require");
    }
}
