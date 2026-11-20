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

    /**
     * Returns the services shared by the whole run, such as the {@link Environment}.
     *
     * <p>Implementations that take part in a run with rules that read the environment must override this.
     *
     * @return the run services
     * @throws UnableToVerifyError by default, because the layout provides none
     */
    default RunServices services() {
        throw new UnableToVerifyError(
                "%s provides no run services, so rules that read the environment or share values cannot run with it. Update it to implement ProjectLayout.services()."
                        .formatted(getClass().getName()));
    }
}
