package archfixtures.classgraph;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.classgraph.ClassGraphContext;

public class SampleRule implements ArchitectureRule<ClassGraphContext> {

    @Override
    public String name() {
        return "Sample";
    }

    @Override
    public Severity severity() {
        return Severity.ERROR;
    }

    @Override
    public Class<ClassGraphContext> contextType() {
        return ClassGraphContext.class;
    }

    @Override
    public void verify(ClassGraphContext context) {}
}
