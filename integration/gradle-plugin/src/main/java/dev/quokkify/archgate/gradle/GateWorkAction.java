package dev.quokkify.archgate.gradle;

import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.launch.GateFailure;
import dev.quokkify.archgate.core.launch.GateLauncher;
import dev.quokkify.archgate.core.launch.LaunchRequest;
import dev.quokkify.archgate.core.launch.ModuleDirs;
import dev.quokkify.archgate.core.launch.VariableLookup;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
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
            throw new GradleException(FailureMessage.describe(e));
        }
    }

    static LaunchRequest request(GateWorkParameters parameters) {
        return new LaunchRequest(
                parameters.getRootPackage().get(),
                parameters.getFailOn().get(),
                parameters.getReportStyle().get(),
                new ModuleDirs(
                        paths(parameters.getMainClassesDirs()),
                        paths(parameters.getTestClassesDirs()),
                        ExistingDirectories.of(parameters.getMainSourceDirs().getFiles()),
                        ExistingDirectories.of(parameters.getTestSourceDirs().getFiles())),
                paths(parameters.getOwnDirs()),
                paths(parameters.getRuleClasspath()),
                Optional.of(parameters.getReportFile().get().getAsFile().toPath()),
                lookup(parameters.getSwitches().get()),
                Optional.of(parameters.getStateFile().get().getAsFile().toPath()));
    }

    /**
     * Returns the lookup of the worker: the CI switches set as Gradle properties, then system properties, then
     * environment variables.
     *
     * @param switches the switches set as Gradle properties; rule variables are never in it
     * @return the lookup
     */
    static Function<String, String> lookup(Map<String, String> switches) {
        Map<String, String> copy = Map.copyOf(switches);
        return VariableLookup.firstNonBlank(List.of(copy::get, System::getProperty, System::getenv));
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
