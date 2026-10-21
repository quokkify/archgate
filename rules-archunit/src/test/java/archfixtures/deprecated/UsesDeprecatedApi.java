package archfixtures.deprecated;

public class UsesDeprecatedApi {

    public String readsField() {
        return LegacyApi.OLD_FIELD;
    }

    public String callsMethod() {
        return LegacyApi.oldMethod();
    }

    public void passesDeprecatedParameter() {
        LegacyApi.withDeprecatedParameter(new LegacyApi.LegacyType());
    }

    public LegacyApi.LegacyType assignsDeprecatedReturn() {
        return LegacyApi.returningDeprecated();
    }

    public LegacyApi constructsDeprecated() {
        return new LegacyApi();
    }
}
