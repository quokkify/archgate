package archfixtures.assertions;

import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

public class SoftAssertTest {

    @Test
    public void collectsSeveralChecks() {
        SoftAssert soft = new SoftAssert();
        soft.assertEquals(1, 1);
        soft.assertAll();
    }
}
