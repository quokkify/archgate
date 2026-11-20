package archfixtures.bridgedcases;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class BridgedOverrideTest extends GenericCheck<String> {

    @Test
    @TmsLink("C130")
    @Override
    public void check(String value) {}
}
