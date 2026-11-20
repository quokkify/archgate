package dev.quokkify.archgate.rules.archunit.testrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

class AutomationTypesTest {

    @Test
    void mapsIdsToNamesFromTheAutomationTypeField() {
        List<FieldRecord> fields = List.of(
                new FieldRecord("custom_tags", "1, Red"),
                new FieldRecord("custom_automation_type", "0, None\r\n 2 , Automated \n\n5, Manual, legacy\n"));

        assertThat(AutomationTypes.parse(fields))
                .containsExactly(Map.entry(0, "None"), Map.entry(2, "Automated"), Map.entry(5, "Manual, legacy"));
    }

    @Test
    void failsWhenTheFieldIsMissing() {
        assertThatThrownBy(() -> AutomationTypes.parse(List.of(new FieldRecord("custom_tags", "1, Red"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type not found");
    }

    @Test
    void failsWhenTheFieldHasNoOptions() {
        assertThatThrownBy(() -> AutomationTypes.parse(List.of(new FieldRecord("custom_automation_type", null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type has no options");
    }

    @Test
    void failsOnAnUnreadableOption() {
        assertThatThrownBy(() -> AutomationTypes.parse(List.of(new FieldRecord("custom_automation_type", "x, Manual"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type has an unreadable option 'x, Manual'");
        assertThatThrownBy(() -> AutomationTypes.parse(List.of(new FieldRecord("custom_automation_type", "3"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type has an unreadable option '3'");
    }

    @Test
    void failsOnADuplicateId() {
        assertThatThrownBy(() -> AutomationTypes.parse(
                        List.of(new FieldRecord("custom_automation_type", "2, Automated\n2, Manual"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type has a duplicate option id 2");
    }

    @Test
    void failsOnANonNumericOrFractionalId() {
        assertThatThrownBy(() -> AutomationTypes.parse(List.of(new FieldRecord("custom_automation_type", "Manual, 5"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type has an unreadable option 'Manual, 5'");
        assertThatThrownBy(
                        () -> AutomationTypes.parse(List.of(new FieldRecord("custom_automation_type", "1.5, Manual"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TestRail case field custom_automation_type has an unreadable option '1.5, Manual'");
    }
}
