package archfixtures.tmslink;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

public class HelperMethods {

    @BeforeMethod
    public void setUp() {}

    @DataProvider
    public Object[][] data() {
        return new Object[0][];
    }

    public void plain() {}
}
