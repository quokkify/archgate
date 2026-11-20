package dev.quokkify.archgate.rules.archunit.tms;

import java.util.Map;

public class ProjectRequiredMarkerRule extends RequiredTestAnnotationRule {

    static final Map<String, String> EXCLUSIONS = Map.of(
            "..setup..", "test setup",
            "archfixtures.requiredannotation.MarkerSetup", "setup marker");

    public ProjectRequiredMarkerRule() {
        super(
                RequiredAnnotation.of("archfixtures.requiredannotation.RequiredMarker")
                        .requiringNonBlankValue(),
                EXCLUSIONS);
    }
}
