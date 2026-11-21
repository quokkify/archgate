package archfixtures.disabled;

import org.junit.jupiter.api.Test;

@Quarantined
public class QuarantinedClass {

    @Test
    void neverRuns() {}
}
