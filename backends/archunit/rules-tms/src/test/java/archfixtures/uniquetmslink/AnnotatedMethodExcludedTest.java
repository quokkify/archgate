package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class AnnotatedMethodExcludedTest {

    @Test
    @SetupOnly
    @TmsLink("C11")
    public void setup() {}

    @Test
    @TmsLink("C11")
    public void real() {}
}
