package archfixtures.deprecated;

/** Fixture: deprecated API surface other fixtures call into. */
public class LegacyApi {

    @Deprecated
    public static String OLD_FIELD = "old";

    @Deprecated
    public LegacyApi() {}

    @Deprecated
    public static String oldMethod() {
        return OLD_FIELD;
    }

    @Deprecated
    public static void withDeprecatedParameter(LegacyType type) {}

    @Deprecated
    public static LegacyType returningDeprecated() {
        return new LegacyType();
    }

    @Deprecated
    public static class LegacyType {}
}
