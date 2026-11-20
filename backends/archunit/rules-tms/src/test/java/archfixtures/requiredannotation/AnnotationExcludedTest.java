package archfixtures.requiredannotation;

import org.testng.annotations.Test;

public class AnnotationExcludedTest {

    @Test
    @MarkerSetup
    public void setup() {}

    @Test
    public void real() {}
}
