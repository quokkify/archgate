package archfixtures.console;

/** Fixture: overloads a Thread or Throwable subclass declares itself do not write to the console. */
public class OwnDumpThread extends Thread {

    public void dumpStack(String target) {}

    public void dump() {
        dumpStack("file");
    }

    public static class OwnTraceException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public void printStackTrace(String target) {}

        public void trace() {
            printStackTrace("file");
        }
    }
}
