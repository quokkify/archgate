package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.UnableToVerifyError;
import java.util.Objects;
import java.util.regex.Pattern;

public final class RootPackage {

    private static final Pattern PACKAGE_NAME = Pattern.compile("^[A-Za-z_$][\\w$]*(\\.[A-Za-z_$][\\w$]*)*$");

    private RootPackage() {}

    public static String validate(String value) {
        if (Objects.isNull(value) || !PACKAGE_NAME.matcher(value).matches()) {
            throw new UnableToVerifyError("rootPackage '%s' is not a Java package name".formatted(value));
        }
        return value;
    }
}
