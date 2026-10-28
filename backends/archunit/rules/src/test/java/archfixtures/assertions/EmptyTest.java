package archfixtures.assertions;

import org.testng.annotations.Test;

public class EmptyTest {

    @Test
    public void runsCodeButChecksNothing() {
        EmptyTest flow = new EmptyTest();
        flow.helper();
    }

    void helper() {}
}
