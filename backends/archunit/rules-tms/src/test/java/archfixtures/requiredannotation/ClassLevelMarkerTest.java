package archfixtures.requiredannotation;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
public class ClassLevelMarkerTest {

    public void unmarked() {}

    @RequiredMarker("API")
    public void marked() {}

    @BeforeMethod
    public void setUp() {}

    @DataProvider
    public Object[][] data() {
        return new Object[0][];
    }

    private void helper() {}
}
