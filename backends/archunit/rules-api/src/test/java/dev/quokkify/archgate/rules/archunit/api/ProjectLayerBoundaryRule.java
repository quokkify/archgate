package dev.quokkify.archgate.rules.archunit.api;

import java.util.Set;

/** The layering the fixtures follow: the domain sits below the web and persistence packages. */
public class ProjectLayerBoundaryRule extends LayerBoundaryRule {

    public ProjectLayerBoundaryRule() {
        super("domain", Set.of("persistence", "web"));
    }
}
