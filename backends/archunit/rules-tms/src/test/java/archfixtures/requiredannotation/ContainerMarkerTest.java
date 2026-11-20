package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class ContainerMarkerTest {

    @Test
    @RequiredMarker("API")
    @RequiredMarker("KAFKA")
    public void repeated() {}
}
