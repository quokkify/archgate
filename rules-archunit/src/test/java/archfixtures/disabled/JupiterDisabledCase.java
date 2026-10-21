package archfixtures.disabled;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class JupiterDisabledCase {

    @Disabled("flaky")
    @Test
    void neverRuns() {}
}
