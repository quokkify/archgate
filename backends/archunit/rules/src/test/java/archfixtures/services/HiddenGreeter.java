package archfixtures.services;

class HiddenGreeter implements Greeter {

    @Override
    public String greet() {
        return "hello";
    }
}
