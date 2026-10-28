package archfixtures.sleep;

public class ThreadSleepNanos {

    public void pause() throws InterruptedException {
        Thread.sleep(100L, 5);
    }
}
