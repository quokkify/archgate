package dev.quokkify.archgate.testing;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public record FixtureSpec(
        String rootPackage,
        List<String> modules,
        Optional<String> gate,
        Map<String, List<String>> dependsOn,
        List<String> testDependencies,
        Map<String, String> ruleLibraries,
        List<String> ownGates,
        Map<String, List<String>> testDependsOn) {

    private static final String DEPENDS_ON_PREFIX = "dependsOn.";
    private static final String TEST_DEPENDS_ON_PREFIX = "testDependsOn.";

    public static FixtureSpec load(Path fixtureDir) {
        Properties properties = PropertyFiles.load(fixtureDir.resolve("fixture.properties"));
        Map<String, List<String>> dependsOn = modulesByPrefix(properties, DEPENDS_ON_PREFIX);
        Map<String, List<String>> testDependsOn = modulesByPrefix(properties, TEST_DEPENDS_ON_PREFIX);
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
                Map.copyOf(ruleLibraries),
                PropertyFiles.csv(properties.getProperty("ownGates")),
                Map.copyOf(testDependsOn));
    }

    private static Map<String, List<String>> modulesByPrefix(Properties properties, String prefix) {
        Map<String, List<String>> modules = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames().stream().sorted().toList()) {
            if (key.startsWith(prefix)) {
                modules.put(key.substring(prefix.length()), PropertyFiles.csv(properties.getProperty(key)));
            }
        }
        return modules;
    }

    public boolean singleModule() {
        return modules.isEmpty();
    }

    public List<String> dependenciesOf(String module) {
        return dependsOn.getOrDefault(module, List.of());
    }

    public List<String> testDependenciesOf(String module) {
        return testDependsOn.getOrDefault(module, List.of());
    }

    /** A module with a gate of its own that checks only that module, next to the main {@link #gate()}. */
    public boolean isOwnGate(String module) {
        return Objects.nonNull(module) && ownGates.contains(module);
    }
}
