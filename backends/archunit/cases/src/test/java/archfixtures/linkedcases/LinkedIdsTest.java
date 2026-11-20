package archfixtures.linkedcases;

import io.qameta.allure.TmsLink;
import io.qameta.allure.TmsLinks;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class LinkedIdsTest {

    @Test
    @TmsLink(" c12 ")
    public void lowerCasePrefixed() {}

    @Test
    @TmsLink("C12")
    public void upperCasePrefixed() {}

    @Test
    @TmsLink("12")
    public void plain() {}

    @Test
    @TmsLink("C12a")
    public void nonNumeric() {}

    @Test
    @TmsLink("0")
    public void zero() {}

    @Test
    @TmsLink("")
    public void empty() {}

    @Test
    @TmsLink("C99999999999")
    public void tooLarge() {}

    @Test
    @TmsLinks({@TmsLink("C13"), @TmsLink("C14")})
    public void container() {}

    @DataProvider
    @TmsLink("C99")
    public Object[][] provider() {
        return new Object[0][0];
    }

    @BeforeMethod
    @TmsLink("C98")
    public void setUp() {}
}
