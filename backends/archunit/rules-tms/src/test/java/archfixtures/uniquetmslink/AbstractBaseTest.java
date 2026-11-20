package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public abstract class AbstractBaseTest {

    @Test
    @TmsLink("C90")
    public void inherited() {}
}
