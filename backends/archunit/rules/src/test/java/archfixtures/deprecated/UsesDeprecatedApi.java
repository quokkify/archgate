package archfixtures.deprecated;

public class UsesDeprecatedApi {

    @SuppressWarnings("deprecation")
    public String readsField() {
        return LegacyApi.OLD_FIELD;
    }

    @SuppressWarnings("deprecation")
    public String callsMethod() {
        return LegacyApi.oldMethod();
    }

    @SuppressWarnings("deprecation")
    public void passesDeprecatedParameter() {
        LegacyApi.withDeprecatedParameter(new LegacyApi.LegacyType());
    }

    @SuppressWarnings("deprecation")
    public LegacyApi.LegacyType assignsDeprecatedReturn() {
        return LegacyApi.returningDeprecated();
    }

    @SuppressWarnings("deprecation")
    public LegacyApi constructsDeprecated() {
        return new LegacyApi();
    }
}
