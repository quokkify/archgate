package archfixtures.sleep;

import static java.lang.Thread.sleep;

public class SleepStaticImport {

    public void pause() throws InterruptedException {
        sleep(5L);
    }
}
