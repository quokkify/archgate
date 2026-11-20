package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

@TmsLink("C6")
public class ClassLevelTmsLinkTest {

    @Test
    public void classOnly() {}
}
