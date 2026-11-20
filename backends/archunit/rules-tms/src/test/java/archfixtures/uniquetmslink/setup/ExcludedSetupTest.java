package archfixtures.uniquetmslink.setup;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class ExcludedSetupTest {

    @Test
    @TmsLink("C20")
    public void setup() {}
}
