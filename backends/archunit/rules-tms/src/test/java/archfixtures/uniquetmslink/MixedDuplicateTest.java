package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class MixedDuplicateTest {

    @Test
    @TmsLink("C60")
    public void one() {}

    @Test
    @TmsLink("C60")
    public void two() {}
}
