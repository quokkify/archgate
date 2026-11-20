package archfixtures.caserules;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class MissingCasesTest {

    @Test
    @TmsLink("C10")
    public void tenB() {}

    @Test
    @TmsLink("C10")
    public void tenA() {}

    @Test
    @TmsLink("C9")
    public void nine() {}
}
