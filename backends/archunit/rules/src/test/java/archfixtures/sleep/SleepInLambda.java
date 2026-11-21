package archfixtures.sleep;

public class SleepInLambda {

    public Runnable pause() {
        return () -> {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
