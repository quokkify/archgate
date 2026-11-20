package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import dev.quokkify.archgate.api.Severity;
import java.io.IOException;

public class DirectlyUnavailableLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "directly-unavailable-launch-rule";
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
        throw ExternalDependencyUnavailable.serviceUnavailable("Tracker", new IOException("Connect timed out"));
    }
}
