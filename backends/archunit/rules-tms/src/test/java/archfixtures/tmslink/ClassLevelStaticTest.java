package archfixtures.tmslink;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

@Test
public class ClassLevelStaticTest {

    @TmsLink("C7")
    public static void staticCase() {}

    public static void main(String[] args) {}

    @TmsLink("C8")
    public static void main() {}
}
