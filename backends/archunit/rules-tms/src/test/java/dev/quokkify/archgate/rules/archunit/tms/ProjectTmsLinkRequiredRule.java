package dev.quokkify.archgate.rules.archunit.tms;

import java.util.Map;

public class ProjectTmsLinkRequiredRule extends TmsLinkRequiredRule {

    static final Map<String, String> EXCLUSIONS = Map.of(
            "..setup..", "test setup",
            "archfixtures.tmslink.SetupOnly", "setup marker");

    public ProjectTmsLinkRequiredRule() {
        super(EXCLUSIONS);
    }
}
