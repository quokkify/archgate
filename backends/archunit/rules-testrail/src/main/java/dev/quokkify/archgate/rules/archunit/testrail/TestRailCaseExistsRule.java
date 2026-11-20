package dev.quokkify.archgate.rules.archunit.testrail;

import dev.quokkify.archgate.cases.archunit.CaseExistsRule;

/**
 * Checks that every {@code @TmsLink} case id on a test method names an existing, not deleted case of the TestRail
 * project in {@code TESTRAIL_PROJECT_ID}.
 *
 * <p>Reads TestRail only when some test links a case, and then needs {@code TESTRAIL_URL}, {@code TESTRAIL_USER},
 * {@code TESTRAIL_PASSWORD} and {@code TESTRAIL_PROJECT_ID}. It shares one load with {@link TestRailCaseAutomatedRule},
 * which reads the automation type field; a missing field does not affect this rule.
 */
public class TestRailCaseExistsRule extends CaseExistsRule {

    /** Creates the rule. */
    public TestRailCaseExistsRule() {
        this(TestRailCaseAutomatedRule.AUTOMATION_FIELD);
    }

    /**
     * Creates the rule for a TestRail instance whose automation type lives in another case field. Pass the field
     * given to your {@link TestRailCaseAutomatedRule} subclass, since both rules share one load and a load for another
     * field makes the second rule fail as unable to verify.
     *
     * @param automationField system name of the case field holding the automation type
     * @throws NullPointerException if {@code automationField} is {@code null}
     * @throws IllegalArgumentException if {@code automationField} is blank or has surrounding whitespace
     */
    protected TestRailCaseExistsRule(String automationField) {
        super(new TestRailCaseService(automationField));
    }
}
