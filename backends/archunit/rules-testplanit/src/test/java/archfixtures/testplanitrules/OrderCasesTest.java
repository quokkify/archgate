package archfixtures.testplanitrules;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class OrderCasesTest {

    @Test
    @TmsLink("C1")
    public void automated() {}

    @Test
    @TmsLink("C2")
    public void manual() {}
}
