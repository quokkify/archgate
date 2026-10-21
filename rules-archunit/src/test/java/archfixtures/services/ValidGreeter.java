package archfixtures.services;

public class ValidGreeter implements Greeter {

    @Override
    public String greet() {
        return "hello";
    }
}
