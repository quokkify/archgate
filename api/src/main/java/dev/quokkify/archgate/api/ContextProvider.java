package dev.quokkify.archgate.api;

/**
 * Builds the {@link AnalysisContext} of one type. Providers are discovered through {@link
 * java.util.ServiceLoader} from {@code META-INF/services/dev.quokkify.archgate.api.ContextProvider}.
 * Exactly one provider per context type must be registered; none or several is an unable-to-verify
 * error.
 *
 * @param <C> the context type built
 */
public interface ContextProvider<C extends AnalysisContext> {

    /**
     * Returns the context type this provider builds.
     *
     * @return the context class
     */
    Class<C> type();

    /**
     * Builds the context. Core calls it lazily, when the first rule asks for the type, and at most once
     * per run per type; the instance is then shared by all rules of that type. When a scope has nothing
     * to analyse under {@link ProjectLayout#rootPackage()}, throw {@link UnableToVerifyError} saying what
     * is missing.
     *
     * @param layout where the project's classes, sources and class loader are
     * @return the context, which must be thread-safe
     * @throws UnableToVerifyError if the context cannot be built
     */
    C create(ProjectLayout layout);
}
