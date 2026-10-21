package dev.quokkify.archgate.api;

public interface ContextProvider<C extends AnalysisContext> {

    Class<C> type();

    C create(ProjectLayout layout);
}
