package archfixtures.caserules;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class ForeignTest {

    @Test
    @TmsLink("C1234567")
    public void linked() {}

    @Test
    @TmsLink("C9")
    public void missing() {}

    @Test
    @TmsLink("C100")
    public void local() {}
}
