package archfixtures.launch;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.Environment;

public record LaunchContext(Environment environment) implements AnalysisContext {}
