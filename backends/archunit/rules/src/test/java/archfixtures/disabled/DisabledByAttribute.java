package archfixtures.disabled;

import org.testng.annotations.Test;

public class DisabledByAttribute {

    @Test(enabled = false)
    public void doesNotRun() {}

    @Test
    public void runs() {}
}
