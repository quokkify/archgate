package archfixtures.tmslink;

import org.testng.annotations.Test;

public class NonPublicTest {

    @Test
    private void hidden() {}

    @Test
    void packagePrivate() {}
}
