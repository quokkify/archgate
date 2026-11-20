package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class OuterMarkerTest {

    public static class NestedTest {

        @Test
        public void nested() {}
    }
}
