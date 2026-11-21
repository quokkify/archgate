package archfixtures.deprecated;

public class CallsInheritedDeprecatedMethod {

    @SuppressWarnings("deprecation")
    public String callsThroughSubclass() {
        return InheritsDeprecatedMethod.oldMethod();
    }
}
