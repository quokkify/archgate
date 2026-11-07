package archfixtures.deprecated;

/** Fixture: a deprecated class whose own members carry no annotation. */
@Deprecated
public class DeprecatedType {

    // not a compile-time constant: javac inlines those, so no field access is left to report
    public static final String CONSTANT = String.valueOf("constant");

    public DeprecatedType() {}

    public static String staticMethod() {
        return CONSTANT;
    }

    public String instanceMethod() {
        return staticMethod();
    }
}
