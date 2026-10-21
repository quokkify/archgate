package archfixtures.registry;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

class PackagePrivateRule implements ArchitectureRule<AnalysisContext> {

    @Override
    public String name() {
        return "package-private-rule";
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
