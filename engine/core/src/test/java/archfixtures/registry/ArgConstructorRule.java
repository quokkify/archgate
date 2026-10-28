package archfixtures.registry;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public class ArgConstructorRule implements ArchitectureRule<AnalysisContext> {

    private final String label;

    public ArgConstructorRule(String label) {
        this.label = label;
    }

    @Override
    public String name() {
        return label;
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
