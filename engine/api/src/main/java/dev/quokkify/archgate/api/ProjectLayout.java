package dev.quokkify.archgate.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Describes the project under analysis to a {@link ContextProvider}.
 */
public interface ProjectLayout {

    /**
     * Returns the package that bounds the analysis.
     *
     * @return the root package name
     */
    String rootPackage();

    /**
     * Returns the compiled-output directories of a scope. The list may include processed-resources
     * directories next to class directories, and paths that do not exist (for example a module without
     * main classes); callers must skip what does not exist rather than fail.
     *
     * @param scope the scope to look at
     * @return output directories, possibly non-existent
     */
    List<Path> classDirs(Scope scope);

    /**
     * Returns the source directories of a scope.
     *
     * @param scope the scope to look at
     * @return existing directories only
     */
    List<Path> sourceDirs(Scope scope);

    /**
     * Returns the class loader that sees the consumer's test runtime classpath.
     *
     * @return the class loader of the gate module's test runtime
     */
    ClassLoader classLoader();
}
