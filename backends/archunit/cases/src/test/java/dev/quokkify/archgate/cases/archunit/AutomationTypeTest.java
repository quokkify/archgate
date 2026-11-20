package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.testng.annotations.Test;

public class AutomationTypeTest {

    @Test(description = "Each kind reads as the actual value printed in a finding")
    public void describesEachKind() {
        assertThat(AutomationType.named("To automate")).hasToString("'To automate'");
        assertThat(AutomationType.notSet()).hasToString("not set");
        assertThat(AutomationType.unknown("77")).hasToString("unknown (77)");
        assertThat(AutomationType.unavailable("no field")).hasToString("unavailable (no field)");
    }

    @Test(description = "Only a named type carries a name")
    public void onlyNamedTypeHasName() {
        assertThat(AutomationType.named("Manual").name()).hasValue("Manual");
        assertThat(AutomationType.notSet().name()).isEmpty();
        assertThat(AutomationType.unknown("77").name()).isEmpty();
        assertThat(AutomationType.unavailable("no field").name()).isEmpty();
    }

    @Test(description = "Only an unavailable type carries a reason")
    public void onlyUnavailableTypeHasReason() {
        assertThat(AutomationType.unavailable("no field").unavailableReason()).hasValue("no field");
        assertThat(AutomationType.unknown("no field").unavailableReason()).isEmpty();
        assertThat(AutomationType.notSet().unavailableReason()).isEmpty();
    }

    @Test(description = "Types compare by kind and value")
    public void comparesByValue() {
        assertThat(AutomationType.named("Automated")).isEqualTo(AutomationType.named("Automated"));
        assertThat(AutomationType.named("Automated")).hasSameHashCodeAs(AutomationType.named("Automated"));
        assertThat(AutomationType.named("77")).isNotEqualTo(AutomationType.unknown("77"));
        assertThat(AutomationType.notSet()).isEqualTo(AutomationType.notSet());
    }

    @Test(description = "A name or raw value must not be blank")
    public void rejectsBlankValues() {
        assertThatThrownBy(() -> AutomationType.named(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AutomationType.unknown("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AutomationType.unavailable(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AutomationType.named(null)).isInstanceOf(NullPointerException.class);
    }
}
