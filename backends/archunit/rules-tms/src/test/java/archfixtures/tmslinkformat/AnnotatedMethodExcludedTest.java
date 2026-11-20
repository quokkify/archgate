package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class AnnotatedMethodExcludedTest {

    @Test
    @SetupOnly
    @TmsLink("bad")
    public void setup() {}

    @Test
    @TmsLink("bad")
    public void real() {}
}
