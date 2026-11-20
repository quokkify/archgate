package dev.quokkify.archgate.rules.archunit.tms;

import java.util.Map;

public class ProjectTmsLinkUniqueRule extends TmsLinkUniqueRule {

    static final Map<String, String> EXCLUSIONS = Map.of(
            "..setup..", "test setup",
            "archfixtures.uniquetmslink.SetupOnly", "setup marker");

    public ProjectTmsLinkUniqueRule() {
        super(EXCLUSIONS);
    }
}
