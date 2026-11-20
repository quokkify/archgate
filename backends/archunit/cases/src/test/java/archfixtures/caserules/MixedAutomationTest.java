package archfixtures.caserules;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class MixedAutomationTest {

    @Test
    @TmsLink("C1")
    public void automated() {}

    @Test
    @TmsLink("C2")
    public void toAutomate() {}

    @Test
    @TmsLink("C3")
    public void unset() {}

    @Test
    @TmsLink("C4")
    public void manual() {}

    @Test
    @TmsLink("C5")
    public void missing() {}
}
