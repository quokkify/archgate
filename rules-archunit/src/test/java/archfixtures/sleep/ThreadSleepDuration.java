package archfixtures.sleep;

import java.time.Duration;

public class ThreadSleepDuration {

    public void pause() throws InterruptedException {
        Thread.sleep(Duration.ofMillis(100));
    }
}
