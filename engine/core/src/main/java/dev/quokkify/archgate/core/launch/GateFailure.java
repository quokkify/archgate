package dev.quokkify.archgate.core.launch;

import dev.quokkify.archgate.core.GateResult;

public final class GateFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient GateResult result;

    public GateFailure(String message, GateResult result) {
        super(message);
        this.result = result;
    }

    public GateResult result() {
        return result;
    }
}
