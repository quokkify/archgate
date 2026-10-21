package archfixtures.throwsclause;

public class GenericThrows {

    public GenericThrows() throws ReflectiveOperationException {}

    public void throwsException() throws Exception {}

    public void throwsRuntimeException() throws RuntimeException {}

    public void throwsThrowable() throws Throwable {}

    public void specificChecked() throws ReflectiveOperationException {}
}
