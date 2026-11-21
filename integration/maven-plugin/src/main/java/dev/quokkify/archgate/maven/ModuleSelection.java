package dev.quokkify.archgate.maven;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

final class ModuleSelection {

    private static final String POM_PACKAGING = "pom";

    private ModuleSelection() {}

    /**
     * Selects the modules to check from the whole reactor and returns their projects in this run.
     *
     * @param current the gate module
     * @param inThisRun the projects of this Maven run, narrowed by {@code -pl}, {@code -rf} or a resume
     * @param reactor every project of the multi-module build
     * @param include the configured {@code modules}, or empty for the default selection
     * @return the selected projects, taken from {@code inThisRun}
     * @throws MojoExecutionException if a listed module is not in the reactor, or a selected module is not in this run
     */
    static List<MavenProject> select(
            MavenProject current, List<MavenProject> inThisRun, List<MavenProject> reactor, List<String> include)
            throws MojoExecutionException {
        List<MavenProject> wanted =
                Objects.isNull(include) || include.isEmpty() ? defaults(current, reactor) : listed(reactor, include);
        Map<String, MavenProject> built = new LinkedHashMap<>();
        inThisRun.forEach(project -> built.put(key(project), project));
        List<String> missing = wanted.stream()
                .filter(project -> !built.containsKey(key(project)))
                .map(MavenProject::getArtifactId)
                .toList();
        if (!missing.isEmpty()) {
            throw new MojoExecutionException(notInThisRun(current, missing));
        }
        return wanted.stream().map(project -> built.get(key(project))).toList();
    }

    private static List<MavenProject> listed(List<MavenProject> reactor, List<String> include)
            throws MojoExecutionException {
        List<MavenProject> selected = new ArrayList<>();
        for (String artifactId : include) {
            selected.add(reactor.stream()
                    .filter(project -> project.getArtifactId().equals(artifactId))
                    .findFirst()
                    .orElseThrow(() -> new MojoExecutionException(
                            "module '%s' listed in <modules> is not part of the reactor".formatted(artifactId))));
        }
        return List.copyOf(selected);
    }

    private static List<MavenProject> defaults(MavenProject current, List<MavenProject> reactor) {
        Set<String> dependencies = new LinkedHashSet<>();
        for (Dependency dependency : current.getDependencies()) {
            dependencies.add(dependency.getGroupId() + ":" + dependency.getArtifactId());
        }
        List<MavenProject> selected = new ArrayList<>();
        selected.add(current);
        reactor.stream()
                .filter(project -> !key(project).equals(key(current)))
                .filter(project -> !POM_PACKAGING.equals(project.getPackaging()))
                .filter(project -> dependencies.contains(key(project)))
                .forEach(selected::add);
        return List.copyOf(selected);
    }

    private static String key(MavenProject project) {
        return project.getGroupId() + ":" + project.getArtifactId();
    }

    private static String notInThisRun(MavenProject current, List<String> missing) {
        boolean one = missing.size() == 1;
        String subject =
                one ? "module %s is".formatted(missing.get(0)) : "modules %s are".formatted(String.join(", ", missing));
        String pronoun = one ? "it" : "them";
        return ("%s part of the reactor but not of this build (-pl, an exclusion, -rf or a resume left %s out), so %s"
                        + " cannot check %s. Include %s in the run: add %s to -pl or use -am, or run without -pl, -rf"
                        + " or an exclusion. To skip the gate in this run instead, pass -Darchgate.skip=true.")
                .formatted(subject, pronoun, current.getArtifactId(), pronoun, pronoun, pronoun);
    }
}
