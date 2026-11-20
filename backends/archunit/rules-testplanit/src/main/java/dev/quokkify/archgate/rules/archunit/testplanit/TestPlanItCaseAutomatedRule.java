package dev.quokkify.archgate.rules.archunit.testplanit;

import dev.quokkify.archgate.cases.archunit.CaseAutomatedRule;
import java.util.Set;

/**
 * Checks that every existing TestPlanIt case linked through {@code @TmsLink} from a test method is marked automated.
 *
 * <p>TestPlanIt keeps a plain automated flag on a case; the rule shows a set flag as the type {@value #AUTOMATED}
 * and a cleared one as {@value #MANUAL}, and by default reports every linked case of type {@value #MANUAL}. A
 * subclass that passes {@value #MANUAL} as a manual type accepts such cases instead. It needs the same variables as
 * {@link TestPlanItCaseExistsRule} and shares one load with it.
 */
public class TestPlanItCaseAutomatedRule extends CaseAutomatedRule {

    /** The type of a case whose automated flag is set. */
    public static final String AUTOMATED = CaseLookup.AUTOMATED;

    /** The type of a case whose automated flag is cleared. */
    public static final String MANUAL = CaseLookup.MANUAL;

    /** Creates the rule, which reports every linked case that is not marked automated. */
    public TestPlanItCaseAutomatedRule() {
        this(Set.of(AUTOMATED), Set.of());
    }

    /**
     * Creates the rule with its own type names.
     *
     * @param automatedTypeNames names of the types that count as automated; not empty
     * @param manualTypeNames names of the types of manual cases, which are never reported
     * @throws NullPointerException if an argument or a name is {@code null}
     * @throws IllegalArgumentException if {@code automatedTypeNames} is empty, a name is blank or a name is in both
     *     sets
     */
    protected TestPlanItCaseAutomatedRule(Set<String> automatedTypeNames, Set<String> manualTypeNames) {
        super(new TestPlanItCaseService(), automatedTypeNames, manualTypeNames);
    }
}
