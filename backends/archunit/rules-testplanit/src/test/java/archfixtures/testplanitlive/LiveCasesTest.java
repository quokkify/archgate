package archfixtures.testplanitlive;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

/** Links the ids the live test creates on a fresh TestPlanIt, whose demo project already holds cases 1 to 5. */
public class LiveCasesTest {

    @Test
    @TmsLink("C6")
    public void automated() {}

    @Test
    @TmsLink("C7")
    public void manual() {}

    @Test
    @TmsLink("C8")
    public void deleted() {}

    @Test
    @TmsLink("C9")
    public void otherProject() {}

    @Test
    @TmsLink("C999999")
    public void missing() {}
}
