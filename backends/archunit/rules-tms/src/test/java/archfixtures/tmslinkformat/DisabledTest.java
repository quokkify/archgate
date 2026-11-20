package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class DisabledTest {

    @Test(enabled = false)
    @TmsLink("bad")
    public void switchedOff() {}
}
