package archfixtures.console;

/** Fixture: javac encodes the unqualified {@code dumpStack} as {@code DumpingThread.dumpStack:()V}. */
public class DumpingThread extends Thread {

    public void dump() {
        dumpStack();
    }
}
