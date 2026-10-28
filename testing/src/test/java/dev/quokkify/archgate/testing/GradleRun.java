package dev.quokkify.archgate.testing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;

public final class GradleRun {

    public static final String CHECK_TASK = ":archgateCheck";

    private GradleRun() {}

    public static GradleRunner runner(Path project, List<String> args) {
        List<String> arguments = new ArrayList<>(List.of("check", "--stacktrace"));
        arguments.addAll(args);
        return GradleRunner.create()
                .withProjectDir(project.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }
}
