package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.BeforeMethod;

public class HelperMethods {

    @BeforeMethod
    @TmsLink("bad")
    public void setUp() {}

    @TmsLink("bad")
    public void helper() {}
}
