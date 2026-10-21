package archfixtures.registry;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;

public class NestedHolder {

    public static class NestedRule implements ArchitectureRule<AnalysisContext> {

        @Override
        public String name() {
            return "nested-rule";
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
}
