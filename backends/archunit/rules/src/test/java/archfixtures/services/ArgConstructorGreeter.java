package archfixtures.services;

public class ArgConstructorGreeter implements Greeter {

    public ArgConstructorGreeter(String name) {}

    @Override
    public String greet() {
        return "hello";
    }
}
