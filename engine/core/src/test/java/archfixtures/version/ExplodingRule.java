package archfixtures.version;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public class ExplodingRule implements ArchitectureRule<AnalysisContext> {

    public ExplodingRule() {
        throw new IllegalStateException("must not be instantiated");
    }

    @Override
    public String name() {
        return "exploding-rule";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Class<AnalysisContext> contextType() {
        return AnalysisContext.class;
    }

    @Override
    public void verify(AnalysisContext context) {}
}
