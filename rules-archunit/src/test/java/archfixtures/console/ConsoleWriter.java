package archfixtures.console;

public class ConsoleWriter {

    public void printToStandardOut() {
        System.out.println("violation");
    }

    public void printToStandardErr() {
        System.err.println("violation");
    }

    public void printTrace(Exception e) {
        e.printStackTrace();
    }

    public void dump() {
        Thread.dumpStack();
    }
}
