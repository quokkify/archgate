package archfixtures.sleep;

public class SleepThroughSubclass {

    public void pause() throws InterruptedException {
        SleepingThread.sleep(5L);
    }
}
