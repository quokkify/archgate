package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class CompensatedCountTest {

    @Test
    @TmsLink("C2")
    @TmsLink("C3")
    public void withLinks() {}

    @Test
    public void withoutLink() {}
}
