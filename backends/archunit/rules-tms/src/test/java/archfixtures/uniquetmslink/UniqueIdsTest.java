package archfixtures.uniquetmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class UniqueIdsTest {

    @Test
    @TmsLink("C10")
    public void first() {}

    @Test
    @TmsLink("C11")
    public void second() {}
}
