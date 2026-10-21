package dev.quokkify.archgate.testing;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public record FixtureSpec(
        String rootPackage,
        List<String> modules,
        Optional<String> gate,
        Map<String, List<String>> dependsOn,
        List<String> testDependencies,
        Map<String, String> ruleLibraries) {

    private static final String DEPENDS_ON_PREFIX = "dependsOn.";

    public static FixtureSpec load(Path fixtureDir) {
        Properties properties = PropertyFiles.load(fixtureDir.resolve("fixture.properties"));
        Map<String, List<String>> dependsOn = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (key.startsWith(DEPENDS_ON_PREFIX)) {
                dependsOn.put(
                        key.substring(DEPENDS_ON_PREFIX.length()), PropertyFiles.csv(properties.getProperty(key)));
            }
        }
        Map<String, String> ruleLibraries = new LinkedHashMap<>();
        for (String entry : PropertyFiles.csv(properties.getProperty("ruleLibrary"))) {
            int separator = entry.indexOf('=');
            if (separator < 1) {
                throw new IllegalArgumentException("Malformed ruleLibrary entry '" + entry + "' in " + fixtureDir);
            }
            ruleLibraries.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        String gate = properties.getProperty("gate", "").trim();
        return new FixtureSpec(
                properties.getProperty("rootPackage", "").trim(),
                PropertyFiles.csv(properties.getProperty("modules")),
                gate.isEmpty() ? Optional.empty() : Optional.of(gate),
                Map.copyOf(dependsOn),
                PropertyFiles.csv(properties.getProperty("testDependencies")),
                Map.copyOf(ruleLibraries));
    }

    public boolean singleModule() {
        return modules.isEmpty();
    }

    public List<String> dependenciesOf(String module) {
        return dependsOn.getOrDefault(module, List.of());
    }
}
