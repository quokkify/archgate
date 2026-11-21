package archfixtures.console;

import java.util.function.Consumer;

public class ConsoleMethodReferences {

    public Runnable dumpStack() {
        return Thread::dumpStack;
    }

    public Consumer<Throwable> printTrace() {
        return Throwable::printStackTrace;
    }

    public Consumer<String> printLine() {
        return System.out::println;
    }
}
