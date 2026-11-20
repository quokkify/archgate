package archfixtures.tmslinkformat;

import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

public class UnicodeIdsTest {

    @Test
    @TmsLink("\u0421123") // Cyrillic capital Es
    public void cyrillicUpper() {}

    @Test
    @TmsLink("\u0441123") // Cyrillic small es
    public void cyrillicLower() {}

    @Test
    @TmsLink("12\u04213") // Cyrillic capital Es inside
    public void cyrillicInside() {}

    @Test
    @TmsLink("\u0661\u0662\u0663") // Arabic-Indic digits
    public void arabicDigits() {}

    @Test
    @TmsLink("\uFF23123") // full-width Latin C
    public void fullWidthC() {}
}
