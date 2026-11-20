package archfixtures.tmslink;

import org.testng.annotations.Test;

@Test
public class ClassLevelExcludedMethodTest {

    @SetupOnly
    public void setup() {}

    public void real() {}
}
