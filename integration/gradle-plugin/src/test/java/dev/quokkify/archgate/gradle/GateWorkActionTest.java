package dev.quokkify.archgate.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import org.testng.annotations.Test;

class GateWorkActionTest {

    private static final String SWITCH = "archgate.ci";
    private static final String SYSTEM_PROPERTY = "java.version";

    @Test
    void switchesComeBeforeSystemProperties() {
        Function<String, String> lookup = GateWorkAction.lookup(Map.of(SYSTEM_PROPERTY, "from-gradle-property"));

        assertThat(lookup.apply(SYSTEM_PROPERTY)).isEqualTo("from-gradle-property");
    }

    @Test
    void namesWithoutASwitchFallBackToSystemProperties() {
        Function<String, String> lookup = GateWorkAction.lookup(Map.of(SWITCH, "true"));

        assertThat(lookup.apply(SWITCH)).isEqualTo("true");
        assertThat(lookup.apply(SYSTEM_PROPERTY)).isEqualTo(System.getProperty(SYSTEM_PROPERTY));
    }
}
