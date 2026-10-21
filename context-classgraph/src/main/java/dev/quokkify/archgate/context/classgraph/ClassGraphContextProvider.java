package dev.quokkify.archgate.context.classgraph;

import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;

public final class ClassGraphContextProvider implements ContextProvider<ClassGraphContext> {

    @Override
    public Class<ClassGraphContext> type() {
        return ClassGraphContext.class;
    }

    @Override
    public ClassGraphContext create(ProjectLayout layout) {
        return new DirectoryClassGraphContext(layout);
    }
}
