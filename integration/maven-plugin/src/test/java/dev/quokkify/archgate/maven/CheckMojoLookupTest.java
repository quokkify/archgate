package dev.quokkify.archgate.maven;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import java.util.function.Function;
import org.testng.annotations.Test;

class CheckMojoLookupTest {

    private static final String ENVIRONMENT_NAME = "PATH";
    private static final String PROPERTY_NAME = "archgate.test.lookup.order";

    @Test
    void userPropertyWinsOverSystemPropertyAndEnvironment() {
        Properties user = new Properties();
        user.setProperty(ENVIRONMENT_NAME, "from-user");
        user.setProperty(PROPERTY_NAME, "from-user");
        System.setProperty(PROPERTY_NAME, "from-system");
        try {
            Function<String, String> lookup = CheckMojo.lookup(user);

            assertThat(System.getenv(ENVIRONMENT_NAME)).isNotBlank();
            assertThat(lookup.apply(ENVIRONMENT_NAME)).isEqualTo("from-user");
            assertThat(lookup.apply(PROPERTY_NAME)).isEqualTo("from-user");
        } finally {
            System.clearProperty(PROPERTY_NAME);
        }
    }

    @Test
    void blankUserPropertyFallsThroughToSystemProperty() {
        Properties user = new Properties();
        user.setProperty(PROPERTY_NAME + ".blank", " ");
        System.setProperty(PROPERTY_NAME + ".blank", "from-system");
        try {
            assertThat(CheckMojo.lookup(user).apply(PROPERTY_NAME + ".blank")).isEqualTo("from-system");
        } finally {
            System.clearProperty(PROPERTY_NAME + ".blank");
        }
    }

    @Test
    void environmentIsReadWithoutProperties() {
        assertThat(CheckMojo.lookup(new Properties()).apply(ENVIRONMENT_NAME))
                .isEqualTo(System.getenv(ENVIRONMENT_NAME));
    }
}
