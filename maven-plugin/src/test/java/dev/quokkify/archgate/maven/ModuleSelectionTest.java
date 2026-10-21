package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

class ModuleSelectionTest {

    private static final String GROUP = "com.acme";

    @Test
    void selectsSelfAndReactorDependencies() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "api");

        assertThat(ModuleSelection.select(tests, List.of(api, other, tests), List.of()))
                .containsExactlyInAnyOrder(tests, api);
    }

    @Test
    void skipsPomPackaging() throws MojoExecutionException {
        MavenProject bom = project("bom", "pom");
        MavenProject tests = project("tests", "jar", "bom");

        assertThat(ModuleSelection.select(tests, List.of(bom, tests), List.of()))
                .containsExactly(tests);
    }

    @Test
    void includeRestricts() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "api");

        assertThat(ModuleSelection.select(tests, List.of(api, other, tests), List.of("other")))
                .containsExactly(other);
    }

    @Test
    void unknownIncludeFails() {
        MavenProject tests = project("tests", "jar");

        assertThatThrownBy(() -> ModuleSelection.select(tests, List.of(tests), List.of("ghost")))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("ghost");
    }

    private static MavenProject project(String artifactId, String packaging, String... dependsOn) {
        Model model = new Model();
        model.setGroupId(GROUP);
        model.setArtifactId(artifactId);
        model.setVersion("1");
        model.setPackaging(packaging);
        for (String name : dependsOn) {
            Dependency dependency = new Dependency();
            dependency.setGroupId(GROUP);
            dependency.setArtifactId(name);
            dependency.setVersion("1");
            model.addDependency(dependency);
        }
        return new MavenProject(model);
    }
}
