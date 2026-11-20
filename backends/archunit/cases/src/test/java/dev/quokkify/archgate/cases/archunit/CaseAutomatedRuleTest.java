package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.caserules.ForeignTest;
import archfixtures.caserules.MixedAutomationTest;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.quokkify.archgate.api.ArchitectureViolation;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.api.ViolationGroup;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class CaseAutomatedRuleTest {

    private static final Set<String> AUTOMATED = Set.of("Automated");
    private static final Set<String> MANUAL = Set.of("Manual");
    private static final CaseIndex INDEX = new CaseIndex(
            Map.of(
                    1, AutomationType.named("Automated"),
                    2, AutomationType.named("To automate"),
                    3, AutomationType.notSet(),
                    4, AutomationType.named("Manual")),
            Set.of(5),
            Set.of(),
            Map.of());
    private static final LinkedCases LINKED =
            LinkedCases.from(new ClassFileImporter().importClasses(MixedAutomationTest.class));

    @Test(description = "One group; a case still to automate names the actual and expected type")
    public void toAutomateReported() {
        List<ViolationGroup> groups = CaseAutomatedRule.violations(LINKED, INDEX, AUTOMATED, MANUAL);

        assertThat(groups).extracting(ViolationGroup::title).containsExactly("unexpected automation type");
        assertThat(groups.get(0).violations())
                .anyMatch(line -> line.matches(
                        "C2  MixedAutomationTest\\.java:\\d+ \\(actual 'To automate', expected 'Automated'\\)"));
    }

    @Test(description = "A case without automation type is reported as not set")
    public void unsetReported() {
        assertThat(lines(LINKED, INDEX, AUTOMATED, MANUAL))
                .anyMatch(line ->
                        line.matches("C3  MixedAutomationTest\\.java:\\d+ \\(actual not set, expected 'Automated'\\)"));
    }

    @Test(description = "Manual cases, automated cases and missing ids are not reported; lines ordered by id")
    public void manualIsNotReported() {
        List<String> lines = lines(LINKED, INDEX, AUTOMATED, MANUAL);

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).startsWith("C2 ");
        assertThat(lines.get(1)).startsWith("C3 ");
    }

    @Test(description = "Missing ids are left to the case-exists rule")
    public void missingIdsSkipped() {
        assertThat(lines(LINKED, INDEX, AUTOMATED, MANUAL)).noneMatch(line -> line.startsWith("C5 "));
    }

    @Test(description = "A narrower automated set turns type Automated into a finding")
    public void customAutomatedSetHonored() {
        assertThat(lines(LINKED, INDEX, Set.of("Automated UI"), MANUAL))
                .anyMatch(line ->
                        line.startsWith("C1 ") && line.endsWith("(actual 'Automated', expected 'Automated UI')"));
    }

    @Test(description = "Several expected types are joined with or, sorted")
    public void severalExpectedTypesJoined() {
        assertThat(lines(LINKED, INDEX, Set.of("Automated UI", "Automated API"), MANUAL))
                .anyMatch(line -> line.startsWith("C1 ")
                        && line.endsWith("(actual 'Automated', expected 'Automated API or Automated UI')"));
    }

    @Test(description = "Configured manual types are never reported, and Manual is reported when not one of them")
    public void manualTypesAreConfigurable() {
        List<String> lines = lines(LINKED, INDEX, AUTOMATED, Set.of("To automate"));

        assertThat(lines).noneMatch(line -> line.startsWith("C2 "));
        assertThat(lines).anyMatch(line -> line.startsWith("C4 ") && line.contains("(actual 'Manual', "));
    }

    @Test(description = "A raw value the service could not name is reported as unknown, not thrown")
    public void unknownTypeNamed() {
        CaseIndex index = new CaseIndex(Map.of(2, AutomationType.unknown("77")), Set.of(), Set.of(), Map.of());

        assertThat(lines(LINKED, index, AUTOMATED, MANUAL))
                .singleElement()
                .asString()
                .endsWith("(actual unknown (77), expected 'Automated')");
    }

    @Test(description = "Without findings no group is returned")
    public void noViolationsNoGroup() {
        CaseIndex index = new CaseIndex(Map.of(1, AutomationType.named("Automated")), Set.of(), Set.of(), Map.of());

        assertThat(CaseAutomatedRule.violations(LINKED, index, AUTOMATED, MANUAL))
                .isEmpty();
    }

    @Test(description = "A foreign id has no case to inspect and is left to the case-exists rule")
    public void foreignIdSkipped() {
        LinkedCases linked = LinkedCases.from(new ClassFileImporter().importClasses(ForeignTest.class));
        CaseIndex index = new CaseIndex(Map.of(), Set.of(9), Set.of(), Map.of(1234567, "2", 100, "7"));

        assertThat(lines(linked, index, AUTOMATED, MANUAL)).isEmpty();
    }

    @Test(description = "A deleted id has no case to inspect and is left to the case-exists rule")
    public void deletedCaseIsNotReported() {
        CaseIndex index = new CaseIndex(Map.of(), Set.of(), Set.of(5), Map.of());

        assertThat(lines(LINKED, index, AUTOMATED, MANUAL)).noneMatch(line -> line.startsWith("C5 "));
    }

    @Test(description = "verify reports the group of the shared index under the service's name")
    public void verifyReportsFindings() {
        ArchUnitContext context = new FixtureContext(
                new ClassFileImporter().importClasses(MixedAutomationTest.class), StubCaseService.CREDENTIALS);
        CaseAutomatedRule rule = new CaseAutomatedRule(StubCaseService.fixed(INDEX), AUTOMATED, MANUAL) {};

        assertThatThrownBy(() -> rule.verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("must identify a Tracker case whose automation type is an automated one")
                .hasMessageContaining("unexpected automation type")
                .hasMessageContaining("actual 'To automate'");
    }

    @Test(description = "The contract mentions manual cases only when manual types are configured")
    public void contractOmitsManualClauseWithoutManualTypes() {
        ArchUnitContext context = new FixtureContext(
                new ClassFileImporter().importClasses(MixedAutomationTest.class), StubCaseService.CREDENTIALS);
        CaseAutomatedRule withManual = new CaseAutomatedRule(StubCaseService.fixed(INDEX), AUTOMATED, MANUAL) {};
        CaseAutomatedRule withoutManual = new CaseAutomatedRule(StubCaseService.fixed(INDEX), AUTOMATED, Set.of()) {};

        assertThatThrownBy(() -> withManual.verify(context)).hasMessageContaining("manual cases are not reported");
        assertThatThrownBy(() -> withoutManual.verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("whose automation type is an automated one.")
                .hasMessageNotContaining("manual cases");
    }

    @Test(description = "A linked case whose automation type is unavailable makes the rule unable to verify")
    public void unavailableTypeIsUnableToVerify() {
        CaseIndex index = new CaseIndex(
                Map.of(1, AutomationType.named("Automated"), 2, AutomationType.unavailable("field x is missing")),
                Set.of(5),
                Set.of(),
                Map.of());

        assertThatThrownBy(() -> CaseAutomatedRule.violations(LINKED, index, AUTOMATED, MANUAL))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("field x is missing");
    }

    @Test(description = "An unavailable type on a case no test links is ignored")
    public void unavailableTypeOfUnlinkedCaseIsIgnored() {
        CaseIndex index = new CaseIndex(
                Map.of(1, AutomationType.named("Automated"), 99, AutomationType.unavailable("field x is missing")),
                Set.of(),
                Set.of(),
                Map.of());

        assertThat(lines(LINKED, index, AUTOMATED, MANUAL)).isEmpty();
    }

    @Test(description = "Rule metadata follows the service's name")
    public void metadata() {
        CaseAutomatedRule rule = new CaseAutomatedRule(StubCaseService.allExisting(), AUTOMATED, MANUAL) {};

        assertThat(rule.name()).isEqualTo("Tracker case automated");
        assertThat(rule.severity()).isEqualTo(Severity.INFO);
        assertThat(rule.scopes()).isEqualTo(EnumSet.of(Scope.TEST));
        assertThat(rule.contextType()).isEqualTo(ArchUnitContext.class);
    }

    @Test(description = "Type names must be given, must not be blank and a name cannot be both automated and manual")
    public void rejectsBadTypeNames() {
        StubCaseService service = StubCaseService.allExisting();

        assertThatThrownBy(() -> new CaseAutomatedRule(service, Set.of(), MANUAL) {})
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseAutomatedRule(service, Set.of(" "), MANUAL) {})
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseAutomatedRule(service, AUTOMATED, Set.of("Automated")) {})
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Automated");
        assertThatThrownBy(() -> new CaseAutomatedRule(service, null, MANUAL) {})
                .isInstanceOf(NullPointerException.class);
    }

    private static List<String> lines(
            LinkedCases linked, CaseIndex index, Set<String> automatedTypeNames, Set<String> manualTypeNames) {
        return CaseAutomatedRule.violations(linked, index, automatedTypeNames, manualTypeNames).stream()
                .flatMap(group -> group.violations().stream())
                .toList();
    }
}
