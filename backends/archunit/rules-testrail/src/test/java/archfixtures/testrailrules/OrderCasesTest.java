package archfixtures.testrailrules;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class OrderCasesTest {

    @Test
    @TmsLink("C1")
    public void automated() {}

    @Test
    @TmsLink("C2")
    public void manual() {}

    @Test
    @TmsLink("C3")
    public void toAutomate() {}

    @Test
    @TmsLink("C4")
    public void typeNotSet() {}

    @Test
    @TmsLink("C5")
    public void typeUnknown() {}
}
