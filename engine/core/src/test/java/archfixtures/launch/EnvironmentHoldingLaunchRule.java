package archfixtures.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Environment;
import dev.quokkify.archgate.api.Severity;
import java.util.List;
import java.util.Objects;

public class EnvironmentHoldingLaunchRule implements ArchitectureRule<LaunchContext> {

    @Override
    public String name() {
        return "environment-holding-launch-rule";
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
        Environment environment = context.environment();
        checkViolations("the service answered", Objects.isNull(environment) ? List.of("no environment") : List.of());
    }
}
