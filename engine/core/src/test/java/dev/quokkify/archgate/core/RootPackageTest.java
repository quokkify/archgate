package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.UnableToVerifyError;
import org.testng.annotations.Test;

class RootPackageTest {

    @Test
    void acceptsPackageName() {
        assertThat(RootPackage.validate("com.acme.tests")).isEqualTo("com.acme.tests");
    }

    @Test
    void rejectsMalformedNames() {
        for (String value : new String[] {"", "com.acme.", "com/acme", "1com"}) {
            assertThatThrownBy(() -> RootPackage.validate(value))
                    .isInstanceOf(UnableToVerifyError.class)
                    .hasMessage("rootPackage '" + value + "' is not a Java package name");
        }
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> RootPackage.validate(null))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("rootPackage 'null' is not a Java package name");
    }
}
