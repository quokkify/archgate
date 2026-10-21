package dev.quokkify.archgate.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Expected result of one build run. {@code contains} and {@code absent} apply to both tools; the
 * tool-scoped lists apply to a single tool. Use {@link #contains(BuildTool)} and {@link #absent(BuildTool)}
 * for the effective lists.
 */
public record Expectation(
        int run,
        List<String> args,
        boolean success,
        boolean skipped,
        List<String> contains,
        List<String> absent,
        List<String> mavenContains,
        List<String> gradleContains,
        List<String> mavenAbsent,
        List<String> gradleAbsent) {

    private static final String MAVEN_PROPERTY_FLAG = "-D";
    private static final String GRADLE_PROPERTY_FLAG = "-P";

    public static List<Expectation> load(Path fixtureDir) {
        Properties properties = PropertyFiles.load(fixtureDir.resolve("expected.properties"));
        int runs = Integer.parseInt(properties.getProperty("runs", "0").trim());
        List<Expectation> expectations = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            String prefix = "run." + run + ".";
            String args = properties.getProperty(prefix + "args", "").trim();
            expectations.add(new Expectation(
                    run,
                    args.isEmpty() ? List.of() : Arrays.asList(args.split("\\s+")),
                    "success"
                            .equals(properties
                                    .getProperty(prefix + "outcome", "success")
                                    .trim()),
                    Boolean.parseBoolean(
                            properties.getProperty(prefix + "skipped", "false").trim()),
                    numbered(properties, prefix + "contains."),
                    numbered(properties, prefix + "absent."),
                    numbered(properties, prefix + "maven.contains."),
                    numbered(properties, prefix + "gradle.contains."),
                    numbered(properties, prefix + "maven.absent."),
                    numbered(properties, prefix + "gradle.absent.")));
        }
        return List.copyOf(expectations);
    }

    public List<String> contains(BuildTool tool) {
        return concat(contains, tool == BuildTool.MAVEN ? mavenContains : gradleContains);
    }

    public List<String> absent(BuildTool tool) {
        return concat(absent, tool == BuildTool.MAVEN ? mavenAbsent : gradleAbsent);
    }

    public List<String> args(BuildTool tool) {
        if (tool == BuildTool.MAVEN) {
            return args;
        }
        return args.stream()
                .map(arg -> arg.startsWith(MAVEN_PROPERTY_FLAG)
                        ? GRADLE_PROPERTY_FLAG + arg.substring(MAVEN_PROPERTY_FLAG.length())
                        : arg)
                .toList();
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static List<String> numbered(Properties properties, String prefix) {
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix)
                        && key.substring(prefix.length()).chars().allMatch(Character::isDigit))
                .sorted(Comparator.comparingInt(key -> Integer.parseInt(key.substring(prefix.length()))))
                .map(properties::getProperty)
                .toList();
    }
}
