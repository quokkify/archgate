package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainDirTest {

    @TempDir
    Path temp;

    @Test
    void missingDirOfDependencyFails() {
        MavenProject module = project(temp.resolve("absent"));

        assertThatThrownBy(() -> MainDir.resolve(module, false))
                .isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("module api is not built");
    }

    @Test
    void missingDirOfCurrentProjectIsSkipped() throws MojoExecutionException {
        assertThat(MainDir.resolve(project(temp.resolve("absent")), true)).isEmpty();
    }

    @Test
    void existingDirIsReturned() throws Exception {
        Path classes = Files.createDirectory(temp.resolve("classes"));

        assertThat(MainDir.resolve(project(classes), false)).contains(classes);
    }

    private static MavenProject project(Path outputDirectory) {
        Model model = new Model();
        model.setGroupId("com.acme");
        model.setArtifactId("api");
        model.setVersion("1");
        Build build = new Build();
        build.setOutputDirectory(outputDirectory.toString());
        model.setBuild(build);
        return new MavenProject(model);
    }
}
