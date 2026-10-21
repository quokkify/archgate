package dev.quokkify.archgate.gradle;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

final class ArchgateElements {

    static final Attribute<String> KIND = Attribute.of("dev.quokkify.archgate.kind", String.class);
    static final String MAIN_CLASSES = "main-classes";
    static final String TEST_CLASSES = "test-classes";
    static final String MAIN_SOURCES = "main-sources";
    static final String TEST_SOURCES = "test-sources";
    static final String ELEMENTS = "archgateElements";
    static final String MODULES = "archgateModules";
    static final String USAGE = "archgate-elements";

    private ArchgateElements() {}

    static void expose(Project project, SourceSet main, SourceSet test) {
        project.getConfigurations().consumable(ELEMENTS, elements -> {
            elements.setDescription("Class and source dirs that other projects check through archgate.modules(...).");
            elements.attributes(attributes -> usage(project, attributes));
            elements.getOutgoing().getVariants().create(MAIN_CLASSES, variant -> classes(variant, main, MAIN_CLASSES));
            elements.getOutgoing().getVariants().create(TEST_CLASSES, variant -> classes(variant, test, TEST_CLASSES));
            ConfigurationVariant mainSources =
                    elements.getOutgoing().getVariants().create(MAIN_SOURCES, variant -> kind(variant, MAIN_SOURCES));
            ConfigurationVariant testSources =
                    elements.getOutgoing().getVariants().create(TEST_SOURCES, variant -> kind(variant, TEST_SOURCES));
            afterEvaluation(project, () -> {
                sources(mainSources, main);
                sources(testSources, test);
            });
        });
    }

    static Configuration consume(Project project, ArchgateExtension extension) {
        project.getDependencies().getAttributesSchema().attribute(KIND);
        DependencyHandler dependencies = project.getDependencies();
        return project.getConfigurations().create(MODULES, modules -> {
            modules.setDescription("Projects named in archgate.modules(...).");
            modules.setCanBeConsumed(false);
            modules.setCanBeResolved(true);
            modules.attributes(attributes -> usage(project, attributes));
            modules.getDependencies()
                    .addAllLater(extension.getModules().map(paths -> projectDependencies(dependencies, paths)));
        });
    }

    static FileCollection files(Configuration modules, String kind) {
        return modules.getIncoming()
                .artifactView(view -> {
                    view.lenient(true);
                    view.attributes(attributes -> attributes.attribute(KIND, kind));
                })
                .getFiles();
    }

    static Provider<ResolvedComponentResult> graph(Configuration modules) {
        return modules.getIncoming().getResolutionResult().getRootComponent();
    }

    private static void afterEvaluation(Project project, Runnable action) {
        if (project.getState().getExecuted()) {
            action.run();
        } else {
            project.afterEvaluate(evaluated -> action.run());
        }
    }

    private static List<Dependency> projectDependencies(DependencyHandler dependencies, List<String> paths) {
        return paths.stream()
                .map(path -> dependencies.project(Map.of("path", path)))
                .toList();
    }

    private static void usage(Project project, AttributeContainer attributes) {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, USAGE));
    }

    private static void kind(ConfigurationVariant variant, String kind) {
        variant.attributes(attributes -> attributes.attribute(KIND, kind));
    }

    private static void classes(ConfigurationVariant variant, SourceSet sourceSet, String kind) {
        kind(variant, kind);
        variant.artifact(sourceSet.getJava().getClassesDirectory(), artifact -> {
            artifact.setType(ArtifactTypeDefinition.JVM_CLASS_DIRECTORY);
            artifact.builtBy(sourceSet.getCompileJavaTaskName());
        });
        variant.artifact(sourceSet.getOutput().getResourcesDir(), artifact -> {
            artifact.setType(ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY);
            artifact.builtBy(sourceSet.getProcessResourcesTaskName());
        });
    }

    private static void sources(ConfigurationVariant variant, SourceSet sourceSet) {
        for (File dir : sourceSet.getJava().getSrcDirs()) {
            variant.artifact(dir, artifact -> artifact.setType(ArtifactTypeDefinition.DIRECTORY_TYPE));
        }
    }

    record RequireArchgateModules(Provider<ResolvedComponentResult> graph) implements Action<Task> {

        private static final String HINT = "archgate: %s is listed in archgate.modules(...) but does not apply"
                + " dev.quokkify.archgate (with the java plugin), so it exposes no " + ELEMENTS
                + ". Apply the plugin in that project.%n  Resolved instead: %s";

        @Override
        public void execute(Task task) {
            for (DependencyResult dependency : graph.get().getDependencies()) {
                if (dependency instanceof UnresolvedDependencyResult unresolved) {
                    throw hint(unresolved.getAttempted().getDisplayName(), firstLine(unresolved.getFailure()));
                }
                if (dependency instanceof ResolvedDependencyResult resolved
                        && !isElements(resolved.getResolvedVariant().getAttributes())) {
                    throw hint(
                            resolved.getRequested().getDisplayName(),
                            "variant " + resolved.getResolvedVariant().getDisplayName());
                }
            }
        }

        private static boolean isElements(AttributeContainer attributes) {
            return attributes.keySet().stream()
                    .filter(key -> key.getName().equals(Usage.USAGE_ATTRIBUTE.getName()))
                    .map(key -> String.valueOf(attributes.getAttribute(key)))
                    .anyMatch(USAGE::equals);
        }

        private static GradleException hint(String module, String resolution) {
            return new GradleException(HINT.formatted(module, resolution));
        }

        private static String firstLine(Throwable failure) {
            String message = failure.getMessage();
            return message == null
                    ? failure.getClass().getName()
                    : message.lines().findFirst().orElse("");
        }
    }
}
