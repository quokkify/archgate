package dev.quokkify.archgate.rules.archunit.testplanit;

import dev.quokkify.archgate.cases.archunit.CaseExistsRule;

/**
 * Checks that every {@code @TmsLink} case id on a test method names an existing, not deleted case of the TestPlanIt
 * project in {@code TESTPLANIT_PROJECT_ID}.
 *
 * <p>Reads TestPlanIt only when some test links a case, and then needs {@code TESTPLANIT_URL},
 * {@code TESTPLANIT_TOKEN} and {@code TESTPLANIT_PROJECT_ID}. It shares one load with
 * {@link TestPlanItCaseAutomatedRule}.
 */
public class TestPlanItCaseExistsRule extends CaseExistsRule {

    /** Creates the rule. */
    public TestPlanItCaseExistsRule() {
        super(new TestPlanItCaseService());
    }
}
