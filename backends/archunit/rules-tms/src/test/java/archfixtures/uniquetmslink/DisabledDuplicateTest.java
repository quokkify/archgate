package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class DisabledDuplicateTest {

    @Test
    @TmsLink("C70")
    public void active() {}

    @Test(enabled = false)
    @TmsLink("C70")
    public void switchedOff() {}
}
