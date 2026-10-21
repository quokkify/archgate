package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApiVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.1.0", "10.20.30", "dev-SNAPSHOT"})
    void parsesSupportedFormats(String text) {
        assertThat(ApiVersion.parse(text)).hasToString(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.2", "1.0.0-rc1", "${archgate.version}", "", " ", "1.0.0.0", "-1.0.0", "a.b.c"})
    void rejectsOtherFormats(String text) {
        assertThatThrownBy(() -> ApiVersion.parse(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparesReleasesNumerically() {
        assertThat(ApiVersion.parse("0.10.0")).isGreaterThan(ApiVersion.parse("0.9.0"));
        assertThat(ApiVersion.parse("1.0.0")).isGreaterThan(ApiVersion.parse("0.99.99"));
        assertThat(ApiVersion.parse("0.1.1")).isGreaterThan(ApiVersion.parse("0.1.0"));
    }

    @Test
    void equalVersionsCompareAsEqual() {
        assertThat(ApiVersion.parse("0.2.0")).isEqualByComparingTo(ApiVersion.parse("0.2.0"));
        assertThat(ApiVersion.parse("dev-SNAPSHOT")).isEqualByComparingTo(ApiVersion.parse("dev-SNAPSHOT"));
    }

    @Test
    void devSnapshotIsNewerThanAnyRelease() {
        assertThat(ApiVersion.parse("dev-SNAPSHOT")).isGreaterThan(ApiVersion.parse("99.0.0"));
        assertThat(ApiVersion.parse("99.0.0")).isLessThan(ApiVersion.parse("dev-SNAPSHOT"));
    }
}
