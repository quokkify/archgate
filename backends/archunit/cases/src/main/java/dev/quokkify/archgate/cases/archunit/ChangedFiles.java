package dev.quokkify.archgate.cases.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.Source;
import dev.quokkify.archgate.api.AnalysisContext;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Paths of the source files touched by a change, one per line relative to the repository root, taken from the file
 * that {@value #VARIABLE} points to. A test class counts as touched when a listed path equals its package path plus
 * source file name, or ends with {@code /} followed by that; nested classes go with their enclosing file.
 */
final class ChangedFiles {

    static final String VARIABLE = "archgate.changedFiles";

    private final List<String> paths;

    private ChangedFiles(List<String> paths) {
        this.paths = paths;
    }

    /**
     * Reads the list, or returns empty for a full check when {@value #VARIABLE} is unset or blank.
     *
     * @throws UnableToVerifyError if {@value #VARIABLE} points to a file that is missing or unreadable
     */
    static Optional<ChangedFiles> from(AnalysisContext context) {
        return context.environment().optional(VARIABLE).map(ChangedFiles::read);
    }

    static ChangedFiles of(List<String> paths) {
        return new ChangedFiles(paths.stream()
                .map(String::strip)
                .filter(path -> !path.isEmpty())
                .map(path -> path.replace('\\', '/'))
                .toList());
    }

    boolean contains(JavaClass javaClass) {
        String key = key(javaClass);
        String suffix = "/" + key;
        return paths.stream().anyMatch(path -> path.equals(key) || path.endsWith(suffix));
    }

    static String classKey(String packageName, Optional<String> sourceFileName, String topLevelSimpleName) {
        String file = sourceFileName.orElse(topLevelSimpleName + ".java");
        return packageName.isEmpty() ? file : packageName.replace('.', '/') + "/" + file;
    }

    private static String key(JavaClass javaClass) {
        JavaClass topLevel = javaClass;
        while (topLevel.getEnclosingClass().isPresent()) {
            topLevel = topLevel.getEnclosingClass().get();
        }
        Optional<String> fileName = javaClass.getSource().flatMap(Source::getFileName);
        return classKey(javaClass.getPackageName(), fileName, topLevel.getSimpleName());
    }

    private static ChangedFiles read(String location) {
        try {
            return of(Files.readAllLines(Path.of(location), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            throw new UnableToVerifyError(
                    "Cannot read the changed-files list '" + location + "' named by " + VARIABLE + ": " + e, e);
        }
    }
}
