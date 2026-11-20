package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import java.io.IOException;

public class UnavailableServiceLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "unavailable-service-launch-rule";
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
        throw context.environment().unavailable("Tracker", new IOException("Connect timed out"));
    }
}
