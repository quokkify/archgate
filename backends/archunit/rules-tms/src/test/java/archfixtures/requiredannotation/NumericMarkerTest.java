package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class NumericMarkerTest {

    @Test
    @NumericMarker(1)
    public void numeric() {}
}
