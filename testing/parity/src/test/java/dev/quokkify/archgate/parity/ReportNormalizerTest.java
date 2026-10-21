package dev.quokkify.archgate.parity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReportNormalizerTest {

    @Test
    // regression: right-aligned duration padding differed between runs
    void durationPaddingDoesNotAffectNormalizedReport() {
        assertThat(ReportNormalizer.normalize("[ERROR] MAIN+TEST     12 ms  Rule"))
                .isEqualTo(ReportNormalizer.normalize("[ERROR] MAIN+TEST    123 ms  Rule"))
                .isEqualTo("[ERROR] MAIN+TEST N ms  Rule");
    }

    @Test
    void normalizesInlineDurations() {
        assertThat(ReportNormalizer.normalize("(3 rules, 12 ms total)")).isEqualTo("(3 rules, N ms total)");
        assertThat(ReportNormalizer.normalize("Model build: all 3 ms, main 5 ms"))
                .isEqualTo("Model build: all N ms, main N ms");
        assertThat(ReportNormalizer.normalize("took  1.2 s")).isEqualTo("took N ms");
    }

    @Test
    void leavesUnrelatedTextUnchanged() {
        assertThat(ReportNormalizer.normalize("5 rules, 12 findings")).isEqualTo("5 rules, 12 findings");
        assertThat(ReportNormalizer.normalize("1.2 services")).isEqualTo("1.2 services");
        assertThat(ReportNormalizer.normalize("3 msgs")).isEqualTo("3 msgs");
        assertThat(ReportNormalizer.normalize("v1.2 sec")).isEqualTo("v1.2 sec");
    }
}
