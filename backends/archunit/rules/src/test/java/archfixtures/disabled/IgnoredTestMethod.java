package archfixtures.disabled;

import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

public class IgnoredTestMethod {

    @Ignore("flaky")
    @Test
    public void neverRuns() {}
}
