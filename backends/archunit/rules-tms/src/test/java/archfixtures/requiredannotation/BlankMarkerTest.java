package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class BlankMarkerTest {

    @Test
    @RequiredMarker(" ")
    public void blank() {}
}
