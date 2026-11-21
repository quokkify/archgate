package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.testng.annotations.Test;

class ModuleSelectionTest {

    private static final String GROUP = "com.acme";

    @Test
    void selectsSelfAndReactorDependencies() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "api");

        assertThat(ModuleSelection.select(tests, List.of(api, other, tests), List.of(api, other, tests), List.of()))
                .containsExactlyInAnyOrder(tests, api);
    }

    @Test
    void skipsPomPackaging() throws MojoExecutionException {
        MavenProject bom = project("bom", "pom");
        MavenProject tests = project("tests", "jar", "bom");

        assertThat(ModuleSelection.select(tests, List.of(bom, tests), List.of(bom, tests), List.of()))
                .containsExactly(tests);
    }

    @Test
    void includeRestricts() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "api");

        assertThat(ModuleSelection.select(
                        tests, List.of(api, other, tests), List.of(api, other, tests), List.of("other")))
                .containsExactly(other);
    }

    @Test
    void unknownIncludeFails() {
        MavenProject tests = project("tests", "jar");

        assertThatThrownBy(() -> ModuleSelection.select(tests, List.of(tests), List.of(tests), List.of("ghost")))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("ghost");
    }

    /** Regression guard: {@code -pl gate} dropped the gate's reactor dependencies and the gate passed unchecked. */
    @Test
    void defaultDependencyOutsideThisRunFails() {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "api", "other");

        assertThatThrownBy(() -> ModuleSelection.select(tests, List.of(tests), List.of(api, other, tests), List.of()))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageStartingWith("modules api, other are part of the reactor but not of this build")
                .hasMessageContaining("so tests cannot check them")
                .hasMessageContaining("add -am to -pl")
                .hasMessageContaining("-Darchgate.skip=true");
    }

    /** Regression guard: a listed module left out of the run failed as if it were not in the reactor at all. */
    @Test
    void listedModuleOutsideThisRunFails() {
        MavenProject api = project("api", "jar");
        MavenProject tests = project("tests", "jar");

        assertThatThrownBy(() -> ModuleSelection.select(tests, List.of(tests), List.of(api, tests), List.of("api")))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageStartingWith("module api is part of the reactor but not of this build");
    }

    @Test
    void dependenciesBuiltInThisRunAreSelected() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "api");

        assertThat(ModuleSelection.select(tests, List.of(api, tests), List.of(api, other, tests), List.of()))
                .containsExactlyInAnyOrder(tests, api);
    }

    @Test
    void listedModulesBuiltInThisRunAreSelected() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject other = project("other", "jar");
        MavenProject tests = project("tests", "jar", "other");

        assertThat(ModuleSelection.select(tests, List.of(api, tests), List.of(api, other, tests), List.of("api")))
                .containsExactly(api);
    }

    @Test
    void returnsTheProjectsOfThisRun() throws MojoExecutionException {
        MavenProject api = project("api", "jar");
        MavenProject apiInRun = project("api", "jar");
        MavenProject tests = project("tests", "jar", "api");

        assertThat(ModuleSelection.select(tests, List.of(apiInRun, tests), List.of(api, tests), List.of()))
                .hasSize(2)
                .anySatisfy(selected -> assertThat(selected).isSameAs(apiInRun))
                .noneSatisfy(selected -> assertThat(selected).isSameAs(api));
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
