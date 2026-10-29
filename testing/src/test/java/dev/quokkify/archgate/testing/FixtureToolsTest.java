package dev.quokkify.archgate.testing;

import static dev.quokkify.archgate.testing.BuildTool.GRADLE;
import static dev.quokkify.archgate.testing.BuildTool.MAVEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import org.testng.annotations.Test;

class FixtureToolsTest {

    @Test
    void parsesBothTools() {
        assertThat(FixtureTools.parse("maven,gradle")).isEqualTo(EnumSet.of(MAVEN, GRADLE));
    }

    @Test
    void parsesSingleToolCaseInsensitively() {
        assertThat(FixtureTools.parse(" Gradle ")).isEqualTo(EnumSet.of(GRADLE));
    }

    @Test
    void rejectsUnknownTool() {
        assertThatThrownBy(() -> FixtureTools.parse("gradel"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gradel");
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> FixtureTools.parse(" , ")).isInstanceOf(IllegalArgumentException.class);
    }
}
