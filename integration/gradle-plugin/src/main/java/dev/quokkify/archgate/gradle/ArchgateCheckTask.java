package dev.quokkify.archgate.gradle;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.core.ExternalPolicy;
import dev.quokkify.archgate.core.launch.GateLauncher;
import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@DisableCachingByDefault(because = "rules may read external services")
public abstract class ArchgateCheckTask extends DefaultTask {

    private static final List<Class<?>> WORKER_CLASSES =
            List.of(GateLauncher.class, ArchitectureRule.class, GateWorkAction.class);
    private static final String MISSING_ROOT_PACKAGE = "archgate: rootPackage is not set for project '%s'."
            + " Set archgate { rootPackage = '...' }, or archgate { skip = true } for a module that only exposes"
            + " archgateElements to a gate.";

    @Input
    @Optional
    public abstract Property<String> getRootPackage();

    @Input
    public abstract Property<String> getFailOn();

    @Input
    public abstract Property<String> getReportStyle();

    /**
     * Returns the {@code archgate.ci} Gradle property, which the worker reads before system properties.
     *
     * @return the value, or none when the property is not set
     */
    @Input
    @Optional
    public abstract Property<String> getCi();

    /**
     * Returns the {@code archgate.offline} Gradle property, which the worker reads before system properties.
     *
     * @return the value, or none when the property is not set
     */
    @Input
    @Optional
    public abstract Property<String> getOffline();

    /**
     * Returns the {@code archgate.ci.serviceUnavailable} Gradle property, which the worker reads before system
     * properties.
     *
     * @return the value, or none when the property is not set
     */
    @Input
    @Optional
    public abstract Property<String> getServiceUnavailable();

    @Classpath
    public abstract ConfigurableFileCollection getRuleClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainClassesDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestClassesDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getMainSourceDirs();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getTestSourceDirs();

    @Internal
    public abstract ConfigurableFileCollection getOwnDirs();

    @Internal
    public abstract Property<ResolvedComponentResult> getModuleGraph();

    @Internal
    public abstract Property<String> getProjectPath();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    /**
     * Returns the file that records whether a rule read the environment in the last run.
     *
     * @return the state file
     */
    @OutputFile
    public abstract RegularFileProperty getStateFile();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void check() {
        if (!getRootPackage().isPresent()) {
            throw new GradleException(
                    MISSING_ROOT_PACKAGE.formatted(getProjectPath().get()));
        }
        List<File> workerClasspath = workerClasspath();
        getLogger().info("ArchGate worker classpath: {}", workerClasspath);
        WorkQueue queue = getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(workerClasspath));
        queue.submit(GateWorkAction.class, parameters -> {
            parameters.getRootPackage().set(getRootPackage());
            parameters.getFailOn().set(getFailOn());
            parameters.getReportStyle().set(getReportStyle());
            parameters.getMainClassesDirs().from(getMainClassesDirs());
            parameters.getTestClassesDirs().from(getTestClassesDirs());
            parameters.getMainSourceDirs().from(getMainSourceDirs());
            parameters.getTestSourceDirs().from(getTestSourceDirs());
            parameters.getOwnDirs().from(getOwnDirs());
            parameters.getRuleClasspath().from(getRuleClasspath());
            parameters.getReportFile().set(getReportFile());
            parameters.getStateFile().set(getStateFile());
            parameters.getSwitches().putAll(switches());
        });
    }

    private Map<String, String> switches() {
        Map<String, String> switches = new LinkedHashMap<>();
        putIfPresent(switches, ExternalPolicy.CI_PROPERTY, getCi());
        putIfPresent(switches, ExternalPolicy.OFFLINE_PROPERTY, getOffline());
        putIfPresent(switches, ExternalPolicy.SERVICE_UNAVAILABLE_PROPERTY, getServiceUnavailable());
        return switches;
    }

    private static void putIfPresent(Map<String, String> switches, String name, Property<String> value) {
        if (value.isPresent()) {
            switches.put(name, value.get());
        }
    }

    static List<File> workerClasspath() {
        return WORKER_CLASSES.stream()
                .map(ArchgateCheckTask::codeSource)
                .distinct()
                .toList();
    }

    private static File codeSource(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (Objects.isNull(source) || Objects.isNull(source.getLocation())) {
            throw new GradleException("Cannot locate the classes of " + type.getName());
        }
        try {
            return new File(source.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new GradleException("Cannot locate the classes of " + type.getName() + ": " + e.getMessage(), e);
        }
    }
}
