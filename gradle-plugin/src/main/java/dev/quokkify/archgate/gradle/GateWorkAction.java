package dev.quokkify.archgate.gradle;

import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.launch.GateFailure;
import dev.quokkify.archgate.core.launch.GateLauncher;
import dev.quokkify.archgate.core.launch.LaunchRequest;
import dev.quokkify.archgate.core.launch.ModuleDirs;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.workers.WorkAction;

public abstract class GateWorkAction implements WorkAction<GateWorkParameters> {

    private static final Logger LOGGER = Logging.getLogger(GateWorkAction.class);

    @Override
    public void execute() {
        try {
            GateLauncher.run(request(getParameters()), GateWorkAction::log);
        } catch (GateFailure | UnableToVerifyError e) {
            throw new GradleException(e.getMessage());
        }
    }

    static LaunchRequest request(GateWorkParameters parameters) {
        return new LaunchRequest(
                parameters.getRootPackage().get(),
                parameters.getFailOn().get(),
                new ModuleDirs(
                        paths(parameters.getMainClassesDirs()),
                        paths(parameters.getTestClassesDirs()),
                        paths(parameters.getMainSourceDirs()),
                        paths(parameters.getTestSourceDirs())),
                paths(parameters.getOwnDirs()),
                paths(parameters.getRuleClasspath()),
                Optional.of(parameters.getReportFile().get().getAsFile().toPath()));
    }

    private static List<Path> paths(FileCollection files) {
        return files.getFiles().stream().map(File::toPath).toList();
    }

    private static void log(Severity level, String line) {
        Consumer<String> target = switch (level) {
            case ERROR -> LOGGER::error;
            case WARNING -> LOGGER::warn;
            case INFO -> LOGGER::lifecycle;
        };
        target.accept(line);
    }
}
