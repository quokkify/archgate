package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

@Test
public class ClassLevelTest {

    @TmsLink("C80")
    public void firstCase() {}

    @TmsLink("C80")
    public void secondCase() {}
}
