package archfixtures.version;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public class SecondVersionedRule implements ArchitectureRule<AnalysisContext> {

    @Override
    public String name() {
        return "second-versioned-rule";
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
