package dev.quokkify.archgate.maven;

import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.launch.GateFailure;
import dev.quokkify.archgate.core.launch.GateLauncher;
import dev.quokkify.archgate.core.launch.LaunchRequest;
import dev.quokkify.archgate.core.launch.ModuleDirs;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(
        name = "check",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true)
public class CheckMojo extends AbstractMojo {

    private static final String REPORT_DIR = "archgate";
    private static final String REPORT_FILE = "report.txt";

    @Parameter(property = "archgate.rootPackage", required = true)
    private String rootPackage;

    @Parameter(property = "archgate.failOn", defaultValue = "WARNING")
    private String failOn;

    @Parameter
    private List<String> modules;

    @Parameter(property = "archgate.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("archgate: skipped");
            return;
        }
        LaunchRequest request = request();
        try {
            GateLauncher.run(request, this::log);
        } catch (GateFailure failure) {
            throw new MojoFailureException(failure.getMessage());
        } catch (UnableToVerifyError error) {
            throw new MojoExecutionException(error.getMessage(), error);
        }
    }

    static List<Path> existing(List<String> roots) {
        return roots.stream().map(Path::of).filter(Files::isDirectory).toList();
    }

    private LaunchRequest request() throws MojoExecutionException {
        List<MavenProject> selected = ModuleSelection.select(project, session.getProjects(), modules);
        List<Path> mainClasses = new ArrayList<>();
        List<Path> testClasses = new ArrayList<>();
        List<Path> mainSources = new ArrayList<>();
        List<Path> testSources = new ArrayList<>();
        for (MavenProject module : selected) {
            MainDir.resolve(module, module == project).ifPresent(mainClasses::add);
            testClasses.add(Path.of(module.getBuild().getTestOutputDirectory()));
            mainSources.addAll(existing(module.getCompileSourceRoots()));
            testSources.addAll(existing(module.getTestCompileSourceRoots()));
        }
        Path report =
                Path.of(project.getBuild().getDirectory()).resolve(REPORT_DIR).resolve(REPORT_FILE);
        return new LaunchRequest(
                rootPackage,
                failOn,
                new ModuleDirs(mainClasses, testClasses, mainSources, testSources),
                ownDirs(),
                testClasspath(),
                Optional.of(report));
    }

    private List<Path> ownDirs() {
        List<Path> own = new ArrayList<>();
        Path main = Path.of(project.getBuild().getOutputDirectory());
        if (Files.isDirectory(main)) {
            own.add(main);
        }
        own.add(Path.of(project.getBuild().getTestOutputDirectory()));
        return own;
    }

    private List<Path> testClasspath() throws MojoExecutionException {
        try {
            return project.getTestClasspathElements().stream().map(Path::of).toList();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot build the test classpath: " + e.getMessage(), e);
        }
    }

    private void log(Severity level, String line) {
        Consumer<String> target = switch (level) {
            case ERROR -> getLog()::error;
            case WARNING -> getLog()::warn;
            case INFO -> getLog()::info;
        };
        target.accept(line);
    }
}
