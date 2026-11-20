package dev.quokkify.archgate.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.quokkify.archgate.api.ExternalDependencyUnavailable;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.ExternalPolicy.ServiceUnavailable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

class ExternalPolicyTest {

    private static ExternalPolicy detect(String... keyValues) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put(keyValues[i], keyValues[i + 1]);
        }
        return ExternalPolicy.detect(values::get);
    }

    @Test
    void ciFromCiVariable() {
        ExternalPolicy ci = detect("CI", "true");
        assertThat(ci.ci()).isTrue();
        assertThat(ci.ciSource()).isEqualTo("CI is set");

        ExternalPolicy notCi = detect("CI", "false");
        assertThat(notCi.ci()).isFalse();
        assertThat(notCi.ciSource()).isEqualTo("no CI variable set");

        ExternalPolicy blank = detect("CI", " ");
        assertThat(blank.ci()).isFalse();
        assertThat(blank.ciSource()).isEqualTo("no CI variable set");

        assertThat(detect().ci()).isFalse();
    }

    @Test
    void ciOverride() {
        ExternalPolicy off = detect("archgate.ci", "FALSE ", "CI", "true");
        assertThat(off.ci()).isFalse();
        assertThat(off.ciSource()).isEqualTo("archgate.ci=FALSE");

        ExternalPolicy on = detect("archgate.ci", "true");
        assertThat(on.ci()).isTrue();
        assertThat(on.ciSource()).isEqualTo("archgate.ci=true");
    }

    @Test
    void invalidCiValueFails() {
        assertThatThrownBy(() -> detect("archgate.ci", "yes"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("Unknown archgate.ci value 'yes'. Use true or false.");
    }

    @Test
    void offlineOutsideCi() {
        ExternalPolicy policy = detect("archgate.offline", "true");
        assertThat(policy.offline()).isTrue();
        assertThat(policy.offlineIgnored()).isFalse();
        assertThat(policy.offlineWarning()).isEmpty();
        assertThat(detect().offline()).isFalse();
    }

    @Test
    void offlineIgnoredInCi() {
        ExternalPolicy policy = detect("archgate.offline", "true", "CI", "true");
        assertThat(policy.offline()).isFalse();
        assertThat(policy.offlineIgnored()).isTrue();
        assertThat(policy.offlineWarning())
                .contains("archgate.offline=true is ignored in CI (CI is set); external rules run.");
        assertThat(detect("CI", "true").offlineWarning()).isEqualTo(Optional.empty());
    }

    @Test
    void serviceUnavailableParsing() {
        assertThat(detect().serviceUnavailable()).isEqualTo(ServiceUnavailable.FAIL);
        assertThat(detect("archgate.ci.serviceUnavailable", " ").serviceUnavailable())
                .isEqualTo(ServiceUnavailable.FAIL);
        assertThat(detect("archgate.ci.serviceUnavailable", " Skip ").serviceUnavailable())
                .isEqualTo(ServiceUnavailable.SKIP);
        assertThatThrownBy(() -> detect("archgate.ci.serviceUnavailable", "maybe"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("Unknown archgate.ci.serviceUnavailable value 'maybe'. Use fail or skip.");
    }

    @DataProvider
    Object[][] skipTable() {
        ExternalDependencyUnavailable missing = ExternalDependencyUnavailable.missingVariables(java.util.List.of("A"));
        ExternalDependencyUnavailable offline = ExternalDependencyUnavailable.offline();
        ExternalDependencyUnavailable service =
                ExternalDependencyUnavailable.serviceUnavailable("Tracker", new RuntimeException("down"));
        return new Object[][] {
            {missing, false, ServiceUnavailable.FAIL, false},
            {missing, true, ServiceUnavailable.FAIL, false},
            {missing, true, ServiceUnavailable.SKIP, false},
            {offline, true, ServiceUnavailable.SKIP, false},
            {offline, false, ServiceUnavailable.SKIP, false},
            {service, false, ServiceUnavailable.FAIL, false},
            {service, false, ServiceUnavailable.SKIP, false},
            {service, true, ServiceUnavailable.FAIL, false},
            {service, true, ServiceUnavailable.SKIP, true},
        };
    }

    @Test(dataProvider = "skipTable")
    void skipsInCiOnlyForServiceUnavailableWithSkip(
            ExternalDependencyUnavailable signal, boolean ci, ServiceUnavailable mode, boolean expected) {
        ExternalPolicy policy = new ExternalPolicy(ci, "source", false, false, mode);

        assertThat(policy.skipsInCi(signal)).isEqualTo(expected);
    }
}
