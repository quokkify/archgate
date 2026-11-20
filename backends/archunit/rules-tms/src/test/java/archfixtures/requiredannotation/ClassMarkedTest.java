package archfixtures.requiredannotation;

import org.testng.annotations.Test;

@RequiredMarker("API")
public class ClassMarkedTest {

    @Test
    public void classOnly() {}
}
