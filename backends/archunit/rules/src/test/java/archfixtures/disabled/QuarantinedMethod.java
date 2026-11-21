package archfixtures.disabled;

import org.junit.jupiter.api.Test;

public class QuarantinedMethod {

    @Quarantined
    @Test
    void neverRuns() {}
}
