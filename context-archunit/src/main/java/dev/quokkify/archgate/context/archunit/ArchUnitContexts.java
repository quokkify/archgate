package dev.quokkify.archgate.context.archunit;

import dev.quokkify.archgate.api.ProjectLayout;

public final class ArchUnitContexts {

    private ArchUnitContexts() {}

    public static ArchUnitContext of(ProjectLayout layout) {
        return new DirectoryArchUnitContext(layout);
    }
}
