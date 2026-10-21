package dev.quokkify.archgate.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

final class MainDir {

    private MainDir() {}

    static Optional<Path> resolve(MavenProject module, boolean current) throws MojoExecutionException {
        Path main = Path.of(module.getBuild().getOutputDirectory());
        if (Files.isDirectory(main)) {
            return Optional.of(main);
        }
        if (current) {
            return Optional.empty();
        }
        throw new MojoExecutionException(
                "module %s is not built: %s does not exist".formatted(module.getArtifactId(), main));
    }
}
