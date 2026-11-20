package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class BlankIdsTest {

    @Test
    @TmsLink("")
    public void empty() {}

    @Test
    @TmsLink(" ")
    public void space() {}
}
