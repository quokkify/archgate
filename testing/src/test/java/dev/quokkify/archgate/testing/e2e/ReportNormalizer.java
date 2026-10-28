package dev.quokkify.archgate.testing.e2e;

import java.util.Arrays;
import java.util.regex.Pattern;

final class ReportNormalizer {

    private static final Pattern MILLIS = Pattern.compile(" +\\d+ ms\\b");
    private static final Pattern SECONDS = Pattern.compile(" +\\d+\\.\\d s\\b");
    private static final Pattern MAVEN_PREFIX = Pattern.compile("^\\[ERROR\\]\\s?");
    private static final Pattern MAVEN_GOAL_WRAPPER = Pattern.compile("^Failed to execute goal .+? on project \\S+: ");
    private static final Pattern MAVEN_HELP_SUFFIX = Pattern.compile(" -> \\[Help \\d+\\]$");
    private static final Pattern GRADLE_CAUSE_PREFIX = Pattern.compile("^> ");

    private ReportNormalizer() {}

    static String normalize(String report) {
        String unified = report.replace("\r\n", "\n").replace('\r', '\n');
        String millis = MILLIS.matcher(unified).replaceAll(" N ms");
        return SECONDS.matcher(millis).replaceAll(" N ms");
    }

    static String messageLine(String log, String needle) {
        return Arrays.stream(log.replace("\r\n", "\n").split("\n"))
                .filter(line -> line.contains(needle))
                .map(ReportNormalizer::unwrap)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No log line contains '" + needle + "'"));
    }

    private static String unwrap(String line) {
        String text = MAVEN_PREFIX.matcher(line).replaceFirst("").strip();
        text = MAVEN_GOAL_WRAPPER.matcher(text).replaceFirst("");
        text = MAVEN_HELP_SUFFIX.matcher(text).replaceFirst("");
        return GRADLE_CAUSE_PREFIX.matcher(text).replaceFirst("");
    }
}
