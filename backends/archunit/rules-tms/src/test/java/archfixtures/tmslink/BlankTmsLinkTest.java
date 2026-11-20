package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class BlankTmsLinkTest {

    @Test
    @TmsLink(" ")
    public void blankLink() {}
}
