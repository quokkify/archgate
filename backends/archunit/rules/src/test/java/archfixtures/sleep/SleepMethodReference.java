package archfixtures.sleep;

import java.util.concurrent.TimeUnit;

public class SleepMethodReference {

    @FunctionalInterface
    interface Pause {
        void pause(long amount) throws InterruptedException;
    }

    public Pause threadSleep() {
        return Thread::sleep;
    }

    public Pause timeUnitSleep() {
        return TimeUnit.MILLISECONDS::sleep;
    }
}
