package dev.quokkify.archgate.context.javaparser;

import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;

public final class JavaParserContextProvider implements ContextProvider<JavaParserContext> {

    @Override
    public Class<JavaParserContext> type() {
        return JavaParserContext.class;
    }

    @Override
    public JavaParserContext create(ProjectLayout layout) {
        return new DirectoryJavaParserContext(layout);
    }
}
