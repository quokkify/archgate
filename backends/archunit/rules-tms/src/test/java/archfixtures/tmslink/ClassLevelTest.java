package archfixtures.tmslink;

import java.util.function.Supplier;
import org.testng.annotations.Test;

@Test
public class ClassLevelTest {

    public void publicCase() {
        Supplier<String> supplier = () -> "value";
        supplier.get();
    }

    private void helper() {}
}
