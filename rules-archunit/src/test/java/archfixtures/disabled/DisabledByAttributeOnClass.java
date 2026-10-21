package archfixtures.disabled;

import org.testng.annotations.Test;

@Test(enabled = false)
public class DisabledByAttributeOnClass {

    public void neverRuns() {}
}
