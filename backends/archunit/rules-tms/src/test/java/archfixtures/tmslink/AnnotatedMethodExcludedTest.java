package archfixtures.tmslink;

import org.testng.annotations.Test;

public class AnnotatedMethodExcludedTest {

    @SetupOnly
    @Test
    public void setup() {}

    @Test
    public void real() {}
}
