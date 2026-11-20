package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class MalformedIdsTest {

    @Test
    @TmsLink("0")
    public void zero() {}

    @Test
    @TmsLink("C0")
    public void prefixedZero() {}

    @Test
    @TmsLink("007")
    public void leadingZero() {}

    @Test
    @TmsLink("<case_id>")
    public void placeholder() {}

    @Test
    @TmsLink("abc")
    public void letters() {}

    @Test
    @TmsLink("12 34")
    public void innerSpace() {}

    @Test
    @TmsLink(" 123")
    public void outerSpace() {}

    @Test
    @TmsLink("ABC-12-X")
    public void prefixed() {}

    @Test
    @TmsLink("c123")
    public void lowercase() {}
}
