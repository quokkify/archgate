package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import io.qameta.allure.TmsLinks;
import org.testng.annotations.Test;

public class MultipleIdsTest {

    @Test
    @TmsLinks({@TmsLink("C20"), @TmsLink("C21")})
    public void severalCases() {}

    @Test
    @TmsLinks({@TmsLink("C22"), @TmsLink("C22")})
    public void sameIdTwice() {}

    @Test
    @TmsLink("C23")
    @TmsLinks({@TmsLink("C23"), @TmsLink("C24")})
    public void linkAndContainer() {}
}
