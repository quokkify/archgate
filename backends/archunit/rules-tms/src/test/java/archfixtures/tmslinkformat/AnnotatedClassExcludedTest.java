package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

@SetupOnly
public class AnnotatedClassExcludedTest {

    @Test
    @TmsLink("bad")
    public void setup() {}
}
