package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Severity;
import java.util.List;
import java.util.function.Consumer;

record TestRules(String name, Severity severity, Consumer<NoContext> body)
        implements ArchitectureRule<TestRules.NoContext> {

    record NoContext() implements AnalysisContext {}

    public static final class NoContextProvider implements ContextProvider<NoContext> {

        @Override
        public Class<NoContext> type() {
            return NoContext.class;
        }

        @Override
        public NoContext create(ProjectLayout layout) {
            return new NoContext();
        }
    }

    @Override
    public Class<NoContext> contextType() {
        return NoContext.class;
    }

    @Override
    public void verify(NoContext context) {
        body.accept(context);
    }

    static ArchitectureRule<?> satisfied(String name, Severity severity) {
        return new TestRules(name, severity, context -> {});
    }

    static ArchitectureRule<?> throwing(String name, Severity severity, Throwable failure) {
        return new TestRules(name, severity, context -> TestRules.<RuntimeException>sneaky(failure));
    }

    static ContextResolver resolver() {
        SimpleProjectLayout layout = new SimpleProjectLayout(
                "x", List.of(), List.of(), List.of(), List.of(), TestRules.class.getClassLoader());
        return new ContextResolver(layout, layout.classLoader());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneaky(Throwable failure) throws T {
        throw (T) failure;
    }
}
