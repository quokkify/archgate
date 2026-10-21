package archfixtures.deprecated;

public class ModernUser {

    public String usesCurrentApi() {
        return new ModernApi().currentMethod();
    }
}
