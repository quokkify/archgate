package archfixtures.registry;

public class SubclassedRule extends ValidRule {

    @Override
    public String name() {
        return "subclassed-rule";
    }
}
