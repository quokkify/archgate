package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import archfixtures.caserules.ExistingCasesTest;
import archfixtures.caserules.ForeignTest;
import archfixtures.caserules.MissingCasesTest;
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

public class CaseExistsRuleTest {

    private static final String MISSING = "case does not exist in Tracker";
    private static final String DELETED = "case is deleted in Tracker";
    private static final String FOREIGN = "case belongs to another Tracker project";
    private static final CaseIndex NOTHING_FOUND = new CaseIndex(Map.of(), Set.of(9, 10), Set.of(), Map.of());
    private static final LinkedCases MISSING_CASES =
            LinkedCases.from(new ClassFileImporter().importClasses(MissingCasesTest.class));

    @Test(description = "Missing ids are one group, ordered by numeric id, with the first method's location")
    public void missingIdsGrouped() {
        List<ViolationGroup> groups = CaseExistsRule.violations("Tracker", MISSING_CASES, NOTHING_FOUND);

        assertThat(groups).extracting(ViolationGroup::title).containsExactly(MISSING);
        assertThat(groups.get(0).violations())
                .satisfiesExactly(
                        line -> assertThat(line).matches("C9   MissingCasesTest\\.java:\\d+"),
                        line -> assertThat(line).matches("C10  MissingCasesTest\\.java:\\d+ \\(\\+1 more\\)"));
    }

    @Test(description = "Ids found in the service pass")
    public void existingIdsPass() {
        LinkedCases linked = LinkedCases.from(new ClassFileImporter().importClasses(ExistingCasesTest.class));
        CaseIndex index = new CaseIndex(Map.of(1, AutomationType.named("Automated")), Set.of(), Set.of(), Map.of());

        assertThat(CaseExistsRule.violations("Tracker", linked, index)).isEmpty();
    }

    @Test(description = "Groups come in a fixed order and a foreign id names its project")
    public void groupsInFixedOrderWithForeignProject() {
        LinkedCases linked = LinkedCases.from(new ClassFileImporter().importClasses(ForeignTest.class));
        CaseIndex index = new CaseIndex(Map.of(), Set.of(9), Set.of(), Map.of(1234567, "2", 100, "Payments"));

        List<ViolationGroup> groups = CaseExistsRule.violations("Tracker", linked, index);

        assertThat(groups).extracting(ViolationGroup::title).containsExactly(MISSING, FOREIGN);
        assertThat(groups.get(0).violations()).singleElement().asString().startsWith("C9  ");
        assertThat(groups.get(1).violations())
                .satisfiesExactly(
                        line -> assertThat(line).matches("C100      ForeignTest\\.java:\\d+ \\(project Payments\\)"),
                        line -> assertThat(line).matches("C1234567  ForeignTest\\.java:\\d+ \\(project 2\\)"));
    }

    @Test(description = "Missing ids come first, deleted ids get their own group")
    public void deletedCaseIsReportedAsDeleted() {
        CaseIndex index = new CaseIndex(Map.of(), Set.of(10), Set.of(9), Map.of());

        List<ViolationGroup> groups = CaseExistsRule.violations("Tracker", MISSING_CASES, index);

        assertThat(groups).extracting(ViolationGroup::title).containsExactly(MISSING, DELETED);
        assertThat(groups.get(0).violations()).singleElement().asString().startsWith("C10  ");
        assertThat(groups.get(1).violations()).singleElement().asString().startsWith("C9  ");
    }

    @Test(description = "verify reports the groups of the shared index under the service's name")
    public void verifyReportsFindings() {
        ArchUnitContext context = new FixtureContext(
                new ClassFileImporter().importClasses(MissingCasesTest.class), StubCaseService.CREDENTIALS);
        CaseExistsRule rule = new CaseExistsRule(StubCaseService.fixed(NOTHING_FOUND)) {};

        assertThatThrownBy(() -> rule.verify(context))
                .isInstanceOf(ArchitectureViolation.class)
                .hasMessageContaining("Every @io.qameta.allure.TmsLink case id on a test method must identify an "
                        + "existing Tracker case.")
                .hasMessageContaining(MISSING)
                .hasMessageContaining("C10");
    }

    @Test(description = "verify passes when every linked case exists")
    public void verifyPassesWhenAllExist() {
        ArchUnitContext context = new FixtureContext(
                new ClassFileImporter().importClasses(ExistingCasesTest.class), StubCaseService.CREDENTIALS);
        CaseExistsRule rule = new CaseExistsRule(StubCaseService.allExisting()) {};

        assertThatCode(() -> rule.verify(context)).doesNotThrowAnyException();
    }

    @Test(description = "Both rules on one context share a single load")
    public void bothRulesShareOneLoad() {
        StubCaseService service = StubCaseService.allExisting();
        ArchUnitContext context = new FixtureContext(
                new ClassFileImporter().importPackages("archfixtures.caserules"), StubCaseService.CREDENTIALS);

        assertThatCode(() -> new CaseExistsRule(service) {}.verify(context)).doesNotThrowAnyException();
        assertThatCode(() -> new CaseAutomatedRule(service, Set.of("Automated"), Set.of("Manual")) {}.verify(context))
                .doesNotThrowAnyException();

        assertThat(service.loads()).isEqualTo(1);
    }

    @Test(description = "Regression guard: a defect in the load fails both rules as unable to verify, not as a finding")
    public void brokenLoadIsUnableToVerifyInBothRules() {
        StubCaseService service = StubCaseService.allExisting().failingWith(new AssertionError("defect"));
        ArchUnitContext context = new FixtureContext(
                new ClassFileImporter().importPackages("archfixtures.caserules"), StubCaseService.CREDENTIALS);

        assertThatThrownBy(() -> new CaseExistsRule(service) {}.verify(context))
                .isExactlyInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("Tracker index could not be built");
        assertThatThrownBy(
                        () -> new CaseAutomatedRule(service, Set.of("Automated"), Set.of("Manual")) {}.verify(context))
                .isExactlyInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("Tracker index could not be built");
        assertThat(service.loads()).isEqualTo(1);
    }

    @Test(description = "Rule metadata follows the service's name")
    public void metadata() {
        CaseExistsRule rule = new CaseExistsRule(StubCaseService.allExisting()) {};

        assertThat(rule.name()).isEqualTo("Tracker case exists");
        assertThat(rule.severity()).isEqualTo(Severity.INFO);
        assertThat(rule.scopes()).isEqualTo(EnumSet.of(Scope.TEST));
        assertThat(rule.contextType()).isEqualTo(ArchUnitContext.class);
    }

    @Test(description = "A service without a usable name is rejected when the rule is created")
    public void rejectsServiceWithoutName() {
        assertThatThrownBy(() -> new CaseExistsRule(null) {}).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> new CaseExistsRule(StubCaseService.allExisting().named(" ")) {})
                .isInstanceOf(IllegalArgumentException.class);
    }
}
