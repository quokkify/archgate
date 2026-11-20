package archfixtures.launch;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.Environment;
import dev.quokkify.archgate.api.RunServices;

public record LaunchContext(RunServices services) implements AnalysisContext {

    @Override
    public Environment environment() {
        return services.environment();
    }
}
