package dev.quokkify.archgate.rules.archunit.tms;

import java.util.Map;

public class ProjectTmsLinkFormatRule extends TmsLinkFormatRule {

    static final Map<String, String> EXCLUSIONS = Map.of(
            "..setup..", "test setup",
            "archfixtures.tmslinkformat.SetupOnly", "setup marker");

    public ProjectTmsLinkFormatRule() {
        super(EXCLUSIONS);
    }
}
