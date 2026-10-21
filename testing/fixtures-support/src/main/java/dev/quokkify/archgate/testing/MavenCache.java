package dev.quokkify.archgate.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public final class MavenCache {

    private static final String PROPERTY = "archgate.mavenCache";
    private static final Path OWN_ARTIFACTS = Path.of("dev", "quokkify");
    private static final String MISS_MARKER_SUFFIX = ".lastUpdated";
    private static final String RESOLVER_STATUS = "resolver-status.properties";

    private final Path shared;

    public MavenCache(Path shared) {
        this.shared = shared;
    }

    public static MavenCache fromSystemProperty() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("System property " + PROPERTY + " is not set");
        }
        return new MavenCache(Path.of(value));
    }

    public String tailArgument() {
        return "-Dmaven.repo.local.tail=" + shared;
    }

    public void promote(Path head) {
        if (!Files.isDirectory(head)) {
            return;
        }
        try (Stream<Path> files = Files.walk(head)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = head.relativize(file);
                if (shareable(relative)) {
                    copyIfAbsent(file, shared.resolve(relative.toString()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot promote " + head + " to " + shared, e);
        }
    }

    private static boolean shareable(Path relative) {
        String name = relative.getFileName().toString();
        return !relative.startsWith(OWN_ARTIFACTS)
                && !name.endsWith(MISS_MARKER_SUFFIX)
                && !RESOLVER_STATUS.equals(name);
    }

    private static void copyIfAbsent(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Path partial =
                Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
        try {
            Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
    }
}
