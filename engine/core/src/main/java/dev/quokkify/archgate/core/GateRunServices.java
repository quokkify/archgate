package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.RunServices;
import java.util.Objects;

/**
 * The {@link RunServices} of one gate run, backed by a {@link VariableEnvironment}.
 *
 * @param environment the environment every context of the run shares
 */
public record GateRunServices(VariableEnvironment environment) implements RunServices {

    /**
     * Creates the services.
     *
     * @param environment the environment every context of the run shares
     */
    public GateRunServices {
        Objects.requireNonNull(environment, "environment");
    }
}
