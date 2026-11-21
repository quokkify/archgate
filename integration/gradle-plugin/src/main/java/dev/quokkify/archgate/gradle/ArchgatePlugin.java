package dev.quokkify.archgate.gradle;

import dev.quokkify.archgate.core.ExternalPolicy;
import dev.quokkify.archgate.core.FailOn;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

public abstract class ArchgatePlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "archgate";
    public static final String TASK_NAME = "archgateCheck";

    private static final String ROOT_PACKAGE_PROPERTY = "archgate.rootPackage";
    private static final String FAIL_ON_PROPERTY = "archgate.failOn";
    private static final String SKIP_PROPERTY = "archgate.skip";
    private static final String REPORT_PATH = "reports/archgate/report.txt";
    private static final String STATE_PATH = "reports/archgate/state.properties";
    private static final String JAVA_PLUGIN = "java";
    private static final String NO_JAVA_PLUGIN = """
            archgate: project '%s' applies dev.quokkify.archgate but not the java plugin, so it has no classes to \
            check and no test runtime classpath to load rules from.
              Apply 'java' (or a plugin that applies it, such as java-library, application, groovy, scala or \
            org.jetbrains.kotlin.jvm) in the project whose src/test/resources lists the rules.
              To check other projects, make that java project the gate and list them in archgate { modules(...) }; \
            a project without the java plugin cannot aggregate modules.
              To apply the plugin to every JVM subproject, use subprojects { plugins.withId('java') { apply plugin: \
            'dev.quokkify.archgate' } }.
              If this project only configures the plugin for others, set archgate { skip = true } in it; \
            -Parchgate.skip=true skips the gate in every project.
              Android projects are not supported.""";
    private static final String UNCONFIGURED =
            "archgate: {} has no JVM plugin and no archgate configuration;" + " nothing to check.";

    @Override
    public void apply(Project project) {
        ArchgateExtension extension = project.getExtensions().create(EXTENSION_NAME, ArchgateExtension.class);
        ProviderFactory providers = project.getProviders();
        extension
                .getFailOn()
                .convention(providers.gradleProperty(FAIL_ON_PROPERTY).orElse(FailOn.DEFAULT.name()));
        extension
                .getSkip()
                .convention(providers
                        .gradleProperty(SKIP_PROPERTY)
                        .map(value -> value.isEmpty() || Boolean.parseBoolean(value))
                        .orElse(false));
        project.getPluginManager().withPlugin(JAVA_PLUGIN, plugin -> register(project, extension));
        // After every project is evaluated, so a convention plugin may still apply java in a late afterEvaluate.
        project.getGradle().projectsEvaluated(gradle -> requireJavaPlugin(project, extension));
    }

    /**
     * Fails a project that asked for a gate without the java plugin. A project whose archgate block sets neither
     * rootPackage nor modules got the plugin from a blanket apply, such as a java-platform subproject, and has
     * nothing to check.
     */
    private static void requireJavaPlugin(Project project, ArchgateExtension extension) {
        if (project.getPluginManager().hasPlugin(JAVA_PLUGIN)
                || extension.getSkip().get()) {
            return;
        }
        boolean configured = extension.getRootPackage().isPresent()
                || !extension.getModules().get().isEmpty();
        if (!configured) {
            project.getLogger().lifecycle(UNCONFIGURED, project.getPath());
            return;
        }
        throw new GradleException(NO_JAVA_PLUGIN.formatted(project.getPath()));
    }

    private static void register(Project project, ArchgateExtension extension) {
        ProviderFactory providers = project.getProviders();
        SourceSetContainer sourceSets =
                project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        ArchgateElements.expose(project, main, test);
        Configuration modules = ArchgateElements.consume(project, extension);
        TaskProvider<ArchgateCheckTask> check = project.getTasks()
                .register(TASK_NAME, ArchgateCheckTask.class, task -> {
                    task.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the ArchGate architecture rules.");
                    task.dependsOn(test.getClassesTaskName());
                    task.getRootPackage()
                            .set(extension.getRootPackage().orElse(providers.gradleProperty(ROOT_PACKAGE_PROPERTY)));
                    task.getProjectPath().set(project.getPath());
                    task.getFailOn().set(extension.getFailOn());
                    task.getCi().set(providers.gradleProperty(ExternalPolicy.CI_PROPERTY));
                    task.getOffline().set(providers.gradleProperty(ExternalPolicy.OFFLINE_PROPERTY));
                    task.getServiceUnavailable()
                            .set(providers.gradleProperty(ExternalPolicy.SERVICE_UNAVAILABLE_PROPERTY));
                    task.getRuleClasspath().from(test.getRuntimeClasspath());
                    task.getMainClassesDirs()
                            .from(
                                    main.getOutput().getClassesDirs(),
                                    main.getOutput().getResourcesDir(),
                                    ArchgateElements.files(modules, ArchgateElements.MAIN_CLASSES));
                    task.getTestClassesDirs()
                            .from(
                                    test.getOutput().getClassesDirs(),
                                    test.getOutput().getResourcesDir(),
                                    ArchgateElements.files(modules, ArchgateElements.TEST_CLASSES));
                    task.getMainSourceDirs()
                            .from(
                                    main.getJava().getSourceDirectories(),
                                    ArchgateElements.files(modules, ArchgateElements.MAIN_SOURCES));
                    task.getTestSourceDirs()
                            .from(
                                    test.getJava().getSourceDirectories(),
                                    ArchgateElements.files(modules, ArchgateElements.TEST_SOURCES));
                    task.getModuleGraph().set(ArchgateElements.graph(modules));
                    task.getOwnDirs()
                            .from(
                                    main.getOutput().getClassesDirs(),
                                    test.getOutput().getClassesDirs());
                    task.getReportFile()
                            .set(project.getLayout().getBuildDirectory().file(REPORT_PATH));
                    task.getStateFile()
                            .set(project.getLayout().getBuildDirectory().file(STATE_PATH));
                    task.getOutputs().upToDateWhen(new EnvironmentNotRead(task.getStateFile()));
                    task.onlyIf(
                            "archgate.skip is not set",
                            new NotSkipped(extension.getSkip(), task.getReportFile(), task.getStateFile()));
                    task.doFirst(
                            "verify archgate.modules",
                            new ArchgateElements.RequireArchgateModules(task.getModuleGraph()));
                });
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(check));
    }

    /** Keeps the task up to date only when the last run recorded that no rule read the environment. */
    private record EnvironmentNotRead(Provider<RegularFile> stateFile) implements Spec<Task> {

        private static final String READ_ENVIRONMENT = "readEnvironment";

        @Override
        public boolean isSatisfiedBy(Task task) {
            Path file = stateFile.get().getAsFile().toPath();
            if (!Files.isRegularFile(file)) {
                return false;
            }
            Properties state = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                state.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                task.getLogger().info("archgate: cannot read the state file {}: {}", file, e.getMessage());
                return false;
            }
            return Boolean.FALSE.toString().equals(state.getProperty(READ_ENVIRONMENT));
        }
    }

    /** Removes the outputs of the last run when skipped, so no stale report can show an old verdict. */
    private record NotSkipped(Provider<Boolean> skip, Provider<RegularFile> report, Provider<RegularFile> state)
            implements Spec<Task> {

        @Override
        public boolean isSatisfiedBy(Task task) {
            if (!skip.get()) {
                return true;
            }
            delete(report);
            delete(state);
            return false;
        }

        private static void delete(Provider<RegularFile> file) {
            Path path = file.get().getAsFile().toPath();
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new GradleException("archgate: cannot delete the output of the last run " + path, e);
            }
        }
    }
}
