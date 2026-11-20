package dev.quokkify.archgate.rules.archunit.testrail;

import dev.quokkify.archgate.cases.archunit.CaseAutomatedRule;
import java.util.Set;

/**
 * Checks that every existing TestRail case linked through {@code @TmsLink} from a test method has an automated
 * automation type, read from the case field {@value #AUTOMATION_FIELD} unless a subclass names another one.
 *
 * <p>By default the type {@value #AUTOMATED} counts as automated and cases of type {@value #MANUAL} are never
 * reported. Subclass the rule to name the types and the field of your TestRail instance. It needs the same variables
 * as {@link TestRailCaseExistsRule} and shares one load with it, so both must name the same field. When the field is
 * missing or its options cannot be read, this rule fails as unable to verify, while the existence check still runs.
 */
public class TestRailCaseAutomatedRule extends CaseAutomatedRule {

    /** The automation type that counts as automated by default. */
    public static final String AUTOMATED = "Automated";

    /** The automation type of manual cases by default. */
    public static final String MANUAL = "Manual";

    /** The system name of the case field that holds the automation type by default. */
    public static final String AUTOMATION_FIELD = CaseRecord.AUTOMATION_TYPE_FIELD;

    /** Creates the rule with the default type names and field. */
    public TestRailCaseAutomatedRule() {
        this(Set.of(AUTOMATED), Set.of(MANUAL));
    }

    /**
     * Creates the rule with the automation type names of your TestRail instance.
     *
     * @param automatedTypeNames names of the types that count as automated; not empty
     * @param manualTypeNames names of the types of manual cases, which are never reported
     * @throws NullPointerException if an argument or a name is {@code null}
     * @throws IllegalArgumentException if {@code automatedTypeNames} is empty, a name is blank or a name is in both
     *     sets
     */
    protected TestRailCaseAutomatedRule(Set<String> automatedTypeNames, Set<String> manualTypeNames) {
        this(automatedTypeNames, manualTypeNames, AUTOMATION_FIELD);
    }

    /**
     * Creates the rule with the automation type names and the automation field of your TestRail instance. Pass the
     * same field to your {@link TestRailCaseExistsRule} subclass, since both rules share one load.
     *
     * @param automatedTypeNames names of the types that count as automated; not empty
     * @param manualTypeNames names of the types of manual cases, which are never reported
     * @param automationField system name of the case field holding the automation type, such as
     *     {@value #AUTOMATION_FIELD}
     * @throws NullPointerException if an argument or a name is {@code null}
     * @throws IllegalArgumentException if {@code automatedTypeNames} is empty, a name is blank, a name is in both
     *     sets, or {@code automationField} is blank or has surrounding whitespace
     */
    protected TestRailCaseAutomatedRule(
            Set<String> automatedTypeNames, Set<String> manualTypeNames, String automationField) {
        super(new TestRailCaseService(automationField), automatedTypeNames, manualTypeNames);
    }
}
