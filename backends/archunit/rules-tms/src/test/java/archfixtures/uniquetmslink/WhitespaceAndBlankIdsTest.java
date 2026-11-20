package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class WhitespaceAndBlankIdsTest {

    @Test
    @TmsLink(" C110 ")
    public void padded() {}

    @Test
    @TmsLink("C110")
    public void canonical() {}

    @Test
    @TmsLink("")
    public void emptyFirst() {}

    @Test
    @TmsLink("")
    public void emptySecond() {}

    @Test
    @TmsLink(" ")
    public void blankFirst() {}

    @Test
    @TmsLink(" ")
    public void blankSecond() {}
}
