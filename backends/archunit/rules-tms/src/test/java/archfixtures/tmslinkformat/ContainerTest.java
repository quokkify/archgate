package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import io.qameta.allure.TmsLinks;
import org.testng.annotations.Test;

public class ContainerTest {

    @Test
    @TmsLinks({@TmsLink("1"), @TmsLink("x")})
    public void oneBad() {}

    @Test
    @TmsLinks({@TmsLink("y"), @TmsLink("z")})
    public void twoBad() {}
}
