package dev.quokkify.archgate.core.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.testng.annotations.Test;

class VariableLookupTest {

    private static final String NAME = "TRACKER_TOKEN";

    @Test
    void propertyWinsOverEnvironment() {
        Function<String, String> lookup =
                VariableLookup.firstNonBlank(List.of(source("from-property"), source("from-environment")));

        assertThat(lookup.apply(NAME)).isEqualTo("from-property");
    }

    @Test
    void blankPropertyFallsThroughToEnvironment() {
        Function<String, String> lookup =
                VariableLookup.firstNonBlank(List.of(source("  "), source("from-environment")));

        assertThat(lookup.apply(NAME)).isEqualTo("from-environment");
    }

    @Test
    void allBlankIsNull() {
        Function<String, String> lookup = VariableLookup.firstNonBlank(List.of(source(""), source(" "), name -> null));

        assertThat(lookup.apply(NAME)).isNull();
    }

    @Test
    void sourcesAreReadAtLookupTime() {
        Map<String, String> values = new HashMap<>();
        Function<String, String> lookup = VariableLookup.firstNonBlank(List.of(values::get));
        values.put(NAME, "later");

        assertThat(lookup.apply(NAME)).isEqualTo("later");
    }

    private static Function<String, String> source(String value) {
        return name -> NAME.equals(name) ? value : null;
    }
}
