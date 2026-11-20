package archfixtures.tmslink;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class ClassLevelConfigTest {

    @BeforeMethod
    public void setUp() {}

    @AfterMethod
    public void tearDown() {}

    @DataProvider
    public Object[][] data() {
        return new Object[0][];
    }

    public String helper() {
        return "value";
    }
}
