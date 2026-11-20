package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

@SetupOnly
public class AnnotatedClassExcludedTest {

    @Test
    @TmsLink("C10")
    public void setup() {}
}
