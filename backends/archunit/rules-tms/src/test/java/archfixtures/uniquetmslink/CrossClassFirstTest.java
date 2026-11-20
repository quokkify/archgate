package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class CrossClassFirstTest {

    @Test
    @TmsLink("C40")
    public void first() {}
}
