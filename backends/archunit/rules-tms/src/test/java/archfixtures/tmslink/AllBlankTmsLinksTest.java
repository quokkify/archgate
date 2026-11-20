package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import io.qameta.allure.TmsLinks;
import org.testng.annotations.Test;

public class AllBlankTmsLinksTest {

    @Test
    @TmsLinks({@TmsLink(" "), @TmsLink("")})
    public void allBlank() {}
}
