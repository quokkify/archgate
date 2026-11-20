package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class CrossClassSecondTest {

    @Test
    @TmsLink("C40")
    public void second() {}

    @Test
    @TmsLink("C60")
    public void alsoMixed() {}
}
