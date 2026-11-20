package dev.quokkify.archgate.api;

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
}
