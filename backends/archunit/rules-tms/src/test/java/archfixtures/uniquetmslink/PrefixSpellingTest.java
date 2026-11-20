package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class PrefixSpellingTest {

    @Test
    @TmsLink("131")
    public void bare() {}

    @Test
    @TmsLink("C131")
    public void prefixed() {}

    @Test
    @TmsLink("132")
    @TmsLink("C132")
    public void bothSpellings() {}
}
