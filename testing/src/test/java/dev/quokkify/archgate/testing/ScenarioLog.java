package dev.quokkify.archgate.testing;

import io.qameta.allure.Allure;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Structures an end-to-end scenario as Allure steps and routes each build run's output to its own log file,
 * {@code build/test-logs/<scenario>/<tool>-run<run>.log}, through the {@code scenario}, {@code tool} and
 * {@code run} thread context keys that {@code log4j2-test.xml} routes on.
 *
 * <p>It logs through the Log4j 2 API rather than SLF4J: the Gradle API on this classpath ships its own SLF4J
 * provider, so the Log4j SLF4J bridge is excluded and SLF4J output never reaches the routing appender.
 */
public final class ScenarioLog {

    private static final Logger LOG = LogManager.getLogger(ScenarioLog.class);
    private static final String SCENARIO = "scenario";
    private static final String TOOL = "tool";
    private static final String RUN = "run";
    private static final String TAG = "tag";

    private ScenarioLog() {}

    public static <T> T step(String name, Supplier<T> action) {
        LOG.info("step {}", name);
        return Allure.step(name, action::get);
    }

    public static <T> T run(String scenario, BuildTool tool, int run, Supplier<T> action) {
        // The routing key needs all three keys; a partial context would name a file after the unresolved lookup.
        ThreadContext.put(SCENARIO, scenario);
        ThreadContext.put(TOOL, toolName(tool));
        ThreadContext.put(RUN, Integer.toString(run));
        try {
            return step("run " + toolName(tool) + " #" + run, action);
        } finally {
            ThreadContext.remove(SCENARIO);
            ThreadContext.remove(TOOL);
            ThreadContext.remove(RUN);
        }
    }

    public static void output(String output) {
        LOG.debug("{}", output);
        Allure.attachment("output", "text/plain", output);
    }

    /** Names the case after its scenario, groups it under the scenario and tags it with the build tools it runs. */
    public static void labels(String scenario, Set<BuildTool> tools) {
        Allure.getLifecycle().updateTest(result -> result.setName(scenario));
        Allure.story(scenario);
        tools.forEach(tool -> Allure.label(TAG, toolName(tool)));
    }

    /** Attaches a gate report to the current step; a run that wrote no report attaches nothing. */
    public static void report(BuildTool tool, Path report) {
        if (!Files.isRegularFile(report)) {
            return;
        }
        try {
            Allure.attachment(toolName(tool) + " report.txt", "text/plain", Files.readString(report));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + report, e);
        }
    }

    public static Path logFile(String scenario, BuildTool tool, int run) {
        return Path.of("build", "test-logs", scenario, toolName(tool) + "-run" + run + ".log");
    }

    public static String toolName(BuildTool tool) {
        return tool.name().toLowerCase(Locale.ROOT);
    }
}
