package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class SameClassDuplicateTest {

    @Test
    @TmsLink("C50")
    public void invalidScope() {}

    @Test
    @TmsLink("C50")
    public void invalidOrder() {}
}
