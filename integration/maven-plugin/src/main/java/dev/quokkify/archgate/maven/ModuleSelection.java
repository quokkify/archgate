package dev.quokkify.archgate.maven;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

final class ModuleSelection {

    private static final String POM_PACKAGING = "pom";

    private ModuleSelection() {}

    static List<MavenProject> select(MavenProject current, List<MavenProject> reactor, List<String> include)
            throws MojoExecutionException {
        if (Objects.isNull(include) || include.isEmpty()) {
            return defaults(current, reactor);
        }
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
                .filter(project -> project != current)
                .filter(project -> !POM_PACKAGING.equals(project.getPackaging()))
                .filter(project -> dependencies.contains(project.getGroupId() + ":" + project.getArtifactId()))
                .forEach(selected::add);
        return List.copyOf(selected);
    }
}
