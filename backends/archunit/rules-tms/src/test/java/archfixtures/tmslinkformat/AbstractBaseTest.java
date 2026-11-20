package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public abstract class AbstractBaseTest {

    @Test
    @TmsLink("bad")
    public void inherited() {}
}
