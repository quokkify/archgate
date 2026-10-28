package dev.quokkify.archgate.gradle;

import dev.quokkify.archgate.core.FailOn;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
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

    @Override
    public void apply(Project project) {
        ArchgateExtension extension = project.getExtensions().create(EXTENSION_NAME, ArchgateExtension.class);
        ProviderFactory providers = project.getProviders();
        extension.getRootPackage().convention(providers.gradleProperty(ROOT_PACKAGE_PROPERTY));
        extension
                .getFailOn()
                .convention(providers.gradleProperty(FAIL_ON_PROPERTY).orElse(FailOn.DEFAULT.name()));
        extension
                .getSkip()
                .convention(providers
                        .gradleProperty(SKIP_PROPERTY)
                        .map(value -> value.isEmpty() || Boolean.parseBoolean(value))
                        .orElse(false));
        project.getPluginManager().withPlugin("java", plugin -> register(project, extension));
    }

    private static void register(Project project, ArchgateExtension extension) {
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
                    task.getRootPackage().set(extension.getRootPackage());
                    task.getProjectPath().set(project.getPath());
                    task.getFailOn().set(extension.getFailOn());
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
                    task.onlyIf("archgate.skip is not set", new NotSkipped(extension.getSkip()));
                    task.doFirst(
                            "verify archgate.modules",
                            new ArchgateElements.RequireArchgateModules(task.getModuleGraph()));
                });
        project.getTasks().named(JavaBasePlugin.CHECK_TASK_NAME).configure(task -> task.dependsOn(check));
    }

    private record NotSkipped(Provider<Boolean> skip) implements Spec<Task> {

        @Override
        public boolean isSatisfiedBy(Task task) {
            return !skip.get();
        }
    }
}
