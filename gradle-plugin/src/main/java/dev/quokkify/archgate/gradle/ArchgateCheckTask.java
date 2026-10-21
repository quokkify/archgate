package dev.quokkify.archgate.gradle;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.core.launch.GateLauncher;
import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public abstract class ArchgateCheckTask extends DefaultTask {

    private static final List<Class<?>> WORKER_CLASSES =
            List.of(GateLauncher.class, ArchitectureRule.class, GateWorkAction.class);

    @Input
    @Optional
    public abstract Property<String> getRootPackage();

    @Input
    public abstract Property<String> getFailOn();

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

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void check() {
        List<File> workerClasspath = workerClasspath();
        getLogger().info("ArchGate worker classpath: {}", workerClasspath);
        WorkQueue queue = getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(workerClasspath));
        queue.submit(GateWorkAction.class, parameters -> {
            parameters.getRootPackage().set(getRootPackage());
            parameters.getFailOn().set(getFailOn());
            parameters.getMainClassesDirs().from(getMainClassesDirs());
            parameters.getTestClassesDirs().from(getTestClassesDirs());
            parameters.getMainSourceDirs().from(getMainSourceDirs());
            parameters.getTestSourceDirs().from(getTestSourceDirs());
            parameters.getOwnDirs().from(getOwnDirs());
            parameters.getRuleClasspath().from(getRuleClasspath());
            parameters.getReportFile().set(getReportFile());
        });
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
