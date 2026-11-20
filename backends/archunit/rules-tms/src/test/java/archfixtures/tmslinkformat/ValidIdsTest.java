package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class ValidIdsTest {

    @Test
    @TmsLink("12345")
    public void numeric() {}

    @Test
    @TmsLink("C24680")
    public void prefixed() {}

    @Test
    @TmsLink("6")
    public void shortId() {}
}
