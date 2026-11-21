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

    @DataProvider
    Object[][] ciMarkers() {
        return new Object[][] {
            {"GITHUB_ACTIONS", "true"},
            {"GITLAB_CI", "true"},
            {"JENKINS_URL", "https://jenkins.example.org/"},
            {"TEAMCITY_VERSION", "2025.07 (build 197242)"},
            {"TF_BUILD", "True"},
            {"BUILDKITE", "true"},
            {"CIRCLECI", "true"},
            {"TRAVIS", "true"},
            {"BITBUCKET_BUILD_NUMBER", "42"},
            {"CODEBUILD_BUILD_ID", "project:b1e6661e-e4f2-4156-9ab9-82a19EXAMPLE"},
        };
    }

    /** Regression guard: Jenkins and TeamCity do not set CI, so their builds were treated as local runs. */
    @Test(dataProvider = "ciMarkers")
    void ciFromVendorVariable(String variable, String value) {
        ExternalPolicy policy = detect(variable, value);

        assertThat(policy.ci()).isTrue();
        assertThat(policy.ciSource()).isEqualTo(variable + " is set");
    }

    @Test(dataProvider = "ciMarkers")
    void blankOrFalseVendorVariableIsNotCi(String variable, String value) {
        assertThat(detect(variable, " ").ci()).isFalse();
        assertThat(detect(variable, "false").ci()).isFalse();
    }

    @Test
    void vendorVariableWinsOverCiFalse() {
        ExternalPolicy policy = detect("CI", "false", "JENKINS_URL", "https://jenkins.example.org/");

        assertThat(policy.ci()).isTrue();
        assertThat(policy.ciSource()).isEqualTo("JENKINS_URL is set");
    }

    @Test
    void ciVariableIsNamedFirst() {
        assertThat(detect("CI", "true", "GITHUB_ACTIONS", "true").ciSource()).isEqualTo("CI is set");
    }

    @Test
    void overrideWinsOverVendorVariable() {
        ExternalPolicy off = detect("archgate.ci", "false", "TEAMCITY_VERSION", "2025.07");
        assertThat(off.ci()).isFalse();
        assertThat(off.ciSource()).isEqualTo("archgate.ci=false");
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
    void explicitFalseOfflineIsNotOffline() {
        ExternalPolicy policy = detect("archgate.offline", " False ", "CI", "true");
        assertThat(policy.offline()).isFalse();
        assertThat(policy.offlineIgnored()).isFalse();
    }

    @Test
    void invalidOfflineValueFails() {
        assertThatThrownBy(() -> detect("archgate.offline", "yes"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessage("Unknown archgate.offline value 'yes'. Use true or false.");
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
