package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class AnnotatedTest {

    @Test
    @TmsLink("C1")
    public void linked() {}
}
