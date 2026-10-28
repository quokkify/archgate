package dev.quokkify.archgate.testing;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Build tools the end-to-end test runs, chosen with {@code -PfixtureTools=maven,gradle}. */
public final class FixtureTools {

    private static final String PROPERTY = "archgate.tools";

    private FixtureTools() {}

    public static Set<BuildTool> fromSystemProperty() {
        String value = System.getProperty(PROPERTY);
        if (Objects.isNull(value)) {
            throw new IllegalStateException("System property " + PROPERTY + " is not set");
        }
        return parse(value);
    }

    public static Set<BuildTool> parse(String csv) {
        Set<BuildTool> tools = EnumSet.noneOf(BuildTool.class);
        for (String part : csv.split(",")) {
            String name = part.strip();
            if (!name.isEmpty()) {
                tools.add(tool(name));
            }
        }
        if (tools.isEmpty()) {
            throw new IllegalArgumentException("No build tool in '" + csv + "'; expected maven, gradle or both");
        }
        return tools;
    }

    private static BuildTool tool(String name) {
        for (BuildTool tool : BuildTool.values()) {
            if (tool.name().equalsIgnoreCase(name)) {
                return tool;
            }
        }
        throw new IllegalArgumentException("Unknown build tool '" + name + "'; expected maven or gradle");
    }
}
