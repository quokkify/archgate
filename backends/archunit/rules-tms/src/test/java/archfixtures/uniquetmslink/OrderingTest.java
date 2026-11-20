package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class OrderingTest {

    @Test
    @TmsLink("C9")
    public void shortIdFirst() {}

    @Test
    @TmsLink("C9")
    public void shortIdSecond() {}

    @Test
    @TmsLink("C100")
    public void longIdFirst() {}

    @Test
    @TmsLink("C100")
    public void longIdSecond() {}
}
