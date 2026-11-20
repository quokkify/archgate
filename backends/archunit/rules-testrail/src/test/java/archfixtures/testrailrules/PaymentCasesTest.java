package archfixtures.testrailrules;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class PaymentCasesTest {

    @Test
    @TmsLink("C9")
    public void missing() {}

    @Test
    @TmsLink("C10")
    public void deleted() {}

    @Test
    @TmsLink("C1234567")
    public void otherProject() {}
}
