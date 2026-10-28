package archfixtures.sleep;

import java.util.concurrent.TimeUnit;

public class TimeUnitSleep {

    public void pause() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
    }
}
