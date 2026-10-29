package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.testsupport.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.testng.annotations.Test;

class MainDirTest {

    @Test
    void missingDirOfDependencyFails() {
        try (TempDir tempDir = TempDir.create()) {
            Path temp = tempDir.path();
            MavenProject module = project(temp.resolve("absent"));

            assertThatThrownBy(() -> MainDir.resolve(module, false))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("module api is not built");
        }
    }

    @Test
    void missingDirOfCurrentProjectIsSkipped() throws MojoExecutionException {
        try (TempDir tempDir = TempDir.create()) {
            Path temp = tempDir.path();
            assertThat(MainDir.resolve(project(temp.resolve("absent")), true)).isEmpty();
        }
    }

    @Test
    void existingDirIsReturned() throws Exception {
        try (TempDir tempDir = TempDir.create()) {
            Path temp = tempDir.path();
            Path classes = Files.createDirectory(temp.resolve("classes"));

            assertThat(MainDir.resolve(project(classes), false)).contains(classes);
        }
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
