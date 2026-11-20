package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

@Test
public class ClassLevelTest {

    @TmsLink("bad")
    public void classLevel() {}
}
