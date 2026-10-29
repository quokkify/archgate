package dev.quokkify.archgate.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** A temporary directory that is deleted recursively on {@link #close()}, which is idempotent; use it in try-with-resources. */
public final class TempDir implements AutoCloseable {

    private final Path path;

    private TempDir(Path path) {
        this.path = path;
    }

    /**
     * Creates a new empty temporary directory.
     *
     * @return the directory handle
     */
    public static TempDir create() {
        try {
            return new TempDir(Files.createTempDirectory("archgate-"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the directory.
     *
     * @return the absolute directory path
     */
    public Path path() {
        return path;
    }

    /**
     * Resolves a child path against the directory.
     *
     * @param other the relative path
     * @return the resolved path
     */
    public Path resolve(String other) {
        return path.resolve(other);
    }

    @Override
    public void close() {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
