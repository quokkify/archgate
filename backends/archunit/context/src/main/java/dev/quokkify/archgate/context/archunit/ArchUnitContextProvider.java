package dev.quokkify.archgate.context.archunit;

import dev.quokkify.archgate.api.ContextProvider;
import dev.quokkify.archgate.api.ProjectLayout;

public final class ArchUnitContextProvider implements ContextProvider<ArchUnitContext> {

    @Override
    public Class<ArchUnitContext> type() {
        return ArchUnitContext.class;
    }

    @Override
    public ArchUnitContext create(ProjectLayout layout) {
        return ArchUnitContexts.of(layout);
    }
}
