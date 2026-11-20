package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class TmsLinksContainerTest {

    @Test
    @TmsLink("C4")
    @TmsLink("C5")
    public void twoLinks() {}
}
