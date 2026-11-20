package dev.quokkify.archgate.api;

import java.util.function.Supplier;

/**
 * Services that exist once per run and are available to every context.
 */
public interface RunServices {

    /**
     * Returns the variables rules may read from outside the build.
     *
     * @return the environment of this run
     */
    Environment environment();

    /**
     * Returns the value shared under {@code name}, building it on first use.
     *
     * <p>The value is built once per gate run and shared by every rule and every context of the run, so a
     * load that several rules need, such as a call to an outside service, happens once. Concurrent callers
     * wait for the first one. A failure is kept and rethrown, as the same instance, to every caller, so a
     * service that is down is asked once; a rule that cannot get the value fails or is skipped as if it had
     * thrown the failure itself. Time spent here is not counted in the calling rule's duration; the report
     * shows the build time of each name instead.
     *
     * <p>Callers that use the same name must use the same type. A build must not ask for its own name.
     *
     * @param name the name of the value, unique within the run
     * @param build builds the value; called at most once per name and run
     * @param <T> the type of the value
     * @return the shared value, possibly {@code null} if {@code build} returned {@code null}
     * @throws UnableToVerifyError by default, because these services cannot share values, or if the build
     *     asks for its own name
     */
    default <T> T shared(String name, Supplier<T> build) {
        throw new UnableToVerifyError(
                "%s cannot share values. Update it to implement RunServices.shared(String, Supplier)."
                        .formatted(getClass().getName()));
    }
}
