package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class PrefixedIdsTest {

    @Test
    @TmsLink("ABC-12-X")
    public void prefixed() {}
}
