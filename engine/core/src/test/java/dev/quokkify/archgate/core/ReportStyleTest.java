package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.UnableToVerifyError;
import org.testng.annotations.Test;

class ReportStyleTest {

    @Test
    void parsesCaseInsensitiveAndTrimmed() {
        assertThat(ReportStyle.parse(" Table ")).isEqualTo(ReportStyle.TABLE);
        assertThat(ReportStyle.parse("classic")).isEqualTo(ReportStyle.CLASSIC);
    }

    @Test
    void blankOrMissingIsClassic() {
        assertThat(ReportStyle.parse(null)).isEqualTo(ReportStyle.CLASSIC);
        assertThat(ReportStyle.parse("  ")).isEqualTo(ReportStyle.CLASSIC);
    }

    @Test
    void detailedIsReservedAndUnableToVerify() {
        assertThatThrownBy(() -> ReportStyle.parse("detailed"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("reportStyle 'detailed' is not available yet. Use classic or table.");
    }

    @Test
    void unknownValueIsUnableToVerify() {
        assertThatThrownBy(() -> ReportStyle.parse("fancy"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("Unknown reportStyle value 'fancy'. Use classic or table.");
    }
}
