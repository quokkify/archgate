package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class CompensatedTest {

    @Test
    @RequiredMarker("API")
    public void withMarker() {}

    @Test
    public void withoutMarker() {}
}
