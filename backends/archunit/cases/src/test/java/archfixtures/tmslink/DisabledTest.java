package archfixtures.tmslink;

import org.testng.annotations.Test;

public class DisabledTest {

    @Test(enabled = false)
    public void switchedOff() {}
}
