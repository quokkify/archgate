package archfixtures.sleep;

/** Fixture: methods named sleep that a Thread subclass declares itself are not Thread.sleep. */
public class OwnSleepThread extends Thread {

    public void sleep(String reason) {}

    public static void sleep() {}

    public void rest() {
        sleep("idle");
        sleep();
    }
}
