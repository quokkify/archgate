package archfixtures.deprecated;

public class UsesDeprecatedType {

    @SuppressWarnings("deprecation")
    private DeprecatedType field;

    @SuppressWarnings("deprecation")
    public String readsConstantOfDeprecatedClass() {
        return DeprecatedType.CONSTANT;
    }

    @SuppressWarnings("deprecation")
    public Object readsEnumConstant() {
        return DeprecatedMode.LOCAL;
    }

    @SuppressWarnings("deprecation")
    public String callsStaticMethodOfDeprecatedClass() {
        return DeprecatedType.staticMethod();
    }

    @SuppressWarnings("deprecation")
    public String callsInstanceMethodOfDeprecatedClass() {
        return field.instanceMethod();
    }

    @SuppressWarnings("deprecation")
    public Object constructsDeprecatedClass() {
        return new DeprecatedType();
    }

    @SuppressWarnings("deprecation")
    public DeprecatedMode returnsDeprecatedType() {
        return null;
    }

    @SuppressWarnings("deprecation")
    public void takesDeprecatedType(DeprecatedType value) {}
}
