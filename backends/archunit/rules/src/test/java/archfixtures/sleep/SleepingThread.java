package archfixtures.sleep;

/** Fixture: javac encodes the unqualified {@code sleep} as {@code SleepingThread.sleep:(J)V}. */
public class SleepingThread extends Thread {

    @Override
    public void run() {
        try {
            sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
