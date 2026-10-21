package archfixtures.disabled;

import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

@Ignore("flaky")
public class IgnoredTestClass {

    @Test
    public void neverRuns() {}
}
