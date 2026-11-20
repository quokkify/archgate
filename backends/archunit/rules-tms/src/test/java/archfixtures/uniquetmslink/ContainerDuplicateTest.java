package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class ContainerDuplicateTest {

    @Test
    @TmsLink("C24")
    public void sameIdAsContainer() {}
}
