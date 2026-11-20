package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class MarkedTest {

    @Test
    @RequiredMarker("API")
    public void marked() {}

    public void helper() {}
}
