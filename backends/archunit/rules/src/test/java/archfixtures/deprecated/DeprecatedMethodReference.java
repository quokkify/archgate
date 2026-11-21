package archfixtures.deprecated;

import java.util.function.Supplier;

public class DeprecatedMethodReference {

    @SuppressWarnings("deprecation")
    public Supplier<String> referencesMethod() {
        return LegacyApi::oldMethod;
    }

    @SuppressWarnings("deprecation")
    public Supplier<LegacyApi> referencesConstructor() {
        return LegacyApi::new;
    }
}
