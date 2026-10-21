package archfixtures.sleep;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;

public class AwaitilityWait {

    public void waitForFlag(AtomicBoolean flag) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(flag::get);
    }
}
