package archfixtures.deprecated;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class DeprecatedSignatureReference {

    @SuppressWarnings("deprecation")
    public static void takesLegacy(LegacyApi.LegacyType type) {}

    @SuppressWarnings("deprecation")
    public static LegacyApi.LegacyType givesLegacy() {
        return null;
    }

    @SuppressWarnings("deprecation")
    public Consumer<LegacyApi.LegacyType> referencesParameter() {
        return DeprecatedSignatureReference::takesLegacy;
    }

    @SuppressWarnings("deprecation")
    public Supplier<LegacyApi.LegacyType> referencesReturn() {
        return DeprecatedSignatureReference::givesLegacy;
    }
}
