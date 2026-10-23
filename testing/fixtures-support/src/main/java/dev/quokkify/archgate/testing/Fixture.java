package dev.quokkify.archgate.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class Fixture {

    private static final String ROOT_PROPERTY = "archgate.fixtures";
    private static final String SPEC_FILE = "fixture.properties";
    private static final List<String> DESCRIPTOR_FILES = List.of(SPEC_FILE, "expected.properties");

    private Fixture() {}

    public static Path root() {
        String value = System.getProperty(ROOT_PROPERTY);
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalStateException("System property " + ROOT_PROPERTY + " is not set");
        }
        return Path.of(value);
    }

    public static List<String> scenarios() {
        try (Stream<Path> entries = Files.list(root())) {
            return entries.filter(dir -> Files.isRegularFile(dir.resolve(SPEC_FILE)))
                    .map(dir -> dir.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list fixtures in " + root(), e);
        }
    }

    public static Path materialize(String scenario, BuildTool tool, Path target, Path itRepo, String archgateVersion) {
        return materialize(scenario, tool, target, itRepo, archgateVersion, PluginMode.TESTKIT);
    }

    public static Path materialize(
            String scenario, BuildTool tool, Path target, Path itRepo, String archgateVersion, PluginMode mode) {
        Path source = root().resolve(scenario);
        FixtureSpec spec = FixtureSpec.load(source);
        copySources(source, target);
        if (tool == BuildTool.MAVEN) {
            MavenProjectWriter.write(scenario, spec, target, itRepo, archgateVersion);
        } else {
            GradleProjectWriter.write(scenario, spec, target, itRepo, archgateVersion, mode);
        }
        return target;
    }

    private static void copySources(Path source, Path target) {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = source.relativize(file);
                if (relative.getNameCount() == 1 && DESCRIPTOR_FILES.contains(relative.toString())) {
                    continue;
                }
                Path destination = target.resolve(relative.toString());
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination);
            }
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy fixture " + source, e);
        }
    }
}
