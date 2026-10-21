package archfixtures.registry;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public abstract class AbstractBaseRule implements ArchitectureRule<AnalysisContext> {

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }
}
