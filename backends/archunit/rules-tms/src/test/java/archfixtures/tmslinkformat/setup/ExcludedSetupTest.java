package archfixtures.tmslinkformat.setup;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class ExcludedSetupTest {

    @Test
    @TmsLink("bad")
    public void setup() {}
}
