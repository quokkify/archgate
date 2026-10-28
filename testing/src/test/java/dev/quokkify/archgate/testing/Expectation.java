package dev.quokkify.archgate.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
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
        boolean unableToVerify,
        List<String> contains,
        List<String> absent,
        List<String> mavenContains,
        List<String> gradleContains,
        List<String> mavenAbsent,
        List<String> gradleAbsent,
        List<Pattern> matches,
        List<Pattern> mavenMatches,
        List<Pattern> gradleMatches) {

    private static final String MAVEN_PROPERTY_FLAG = "-D";
    private static final String GRADLE_PROPERTY_FLAG = "-P";
    private static final Pattern RUN_KEY = Pattern.compile(
            "run\\.(\\d+)\\.(args|outcome|skipped|unableToVerify|((maven|gradle)\\.)?(contains|absent|matches)\\.\\d+)");

    public static List<Expectation> load(Path fixtureDir) {
        Properties properties = PropertyFiles.load(fixtureDir.resolve("expected.properties"));
        int runs = Integer.parseInt(properties.getProperty("runs", "0").trim());
        validateKeys(properties, runs, fixtureDir.resolve("expected.properties"));
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
                    Boolean.parseBoolean(properties
                            .getProperty(prefix + "unableToVerify", "false")
                            .trim()),
                    numbered(properties, prefix + "contains."),
                    numbered(properties, prefix + "absent."),
                    numbered(properties, prefix + "maven.contains."),
                    numbered(properties, prefix + "gradle.contains."),
                    numbered(properties, prefix + "maven.absent."),
                    numbered(properties, prefix + "gradle.absent."),
                    patterns(numbered(properties, prefix + "matches.")),
                    patterns(numbered(properties, prefix + "maven.matches.")),
                    patterns(numbered(properties, prefix + "gradle.matches."))));
        }
        return List.copyOf(expectations);
    }

    public List<Pattern> matches(BuildTool tool) {
        return Stream.concat(matches.stream(), (tool == BuildTool.MAVEN ? mavenMatches : gradleMatches).stream())
                .toList();
    }

    private static void validateKeys(Properties properties, int runs, Path file) {
        for (String key : properties.stringPropertyNames()) {
            if (key.equals("runs")) {
                continue;
            }
            java.util.regex.Matcher matcher = RUN_KEY.matcher(key);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unknown or malformed key '" + key + "' in " + file);
            }
            int run = Integer.parseInt(matcher.group(1));
            if (run < 1 || run > runs) {
                throw new IllegalArgumentException(
                        "Key '" + key + "' in " + file + " refers to run " + run + " but runs=" + runs);
            }
        }
    }

    private static List<Pattern> patterns(List<String> regexes) {
        return regexes.stream().map(Pattern::compile).toList();
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
