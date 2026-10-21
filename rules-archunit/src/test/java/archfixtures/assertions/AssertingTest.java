package archfixtures.assertions;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class AssertingTest {

    @Test
    public void checksTotal() {
        assertEquals(2 + 2, 4);
    }

    @Test
    public void callsHelperWithoutAsserting() {
        helper();
    }

    private void helper() {}
}
