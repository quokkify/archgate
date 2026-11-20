package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import dev.quokkify.archgate.context.archunit.support.ArchRuleChecks;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks that each {@code @TmsLink} value on a TestNG test method has the shape of a case id. The value is compared
 * as written, so leading or trailing whitespace counts as malformed; blank values belong to
 * {@link TmsLinkRequiredRule}.
 */
public class TmsLinkFormatRule implements ArchitectureRule<ArchUnitContext> {

    /**
     * Default shape of a case id: a positive integer, optionally preceded by the Latin letter {@code C}, for instance
     * {@code 123} or {@code C123}.
     */
    public static final Pattern DEFAULT_FORMAT = Pattern.compile("C?[1-9]\\d*");

    private static final String EXPECTED_CONTRACT = """
      Every non-blank @io.qameta.allure.TmsLink value on a TestNG test method is a TestRail case id \
      matching %s%s.""";

    private static final String DEFAULT_EXAMPLES = " (for example 123 or C123)";

    private final Pattern format;
    private final String expectedContract;
    private final Map<String, String> exclusionsWithReason;

    /** Checks every test against {@link #DEFAULT_FORMAT}, excluding nothing. */
    public TmsLinkFormatRule() {
        this(DEFAULT_FORMAT, Map.of());
    }

    /**
     * Called by subclasses to keep {@link #DEFAULT_FORMAT} and skip some tests.
     *
     * @param exclusionsWithReason maps each exclusion key to the justification for it. A key with {@code ..} or
     *     {@code *} is read as an ArchUnit package pattern; any other key names an annotation, and a class or method
     *     bearing it is skipped. The map is defensively copied.
     * @throws NullPointerException if the map, or any key or reason in it, is {@code null}
     */
    protected TmsLinkFormatRule(Map<String, String> exclusionsWithReason) {
        this(DEFAULT_FORMAT, exclusionsWithReason);
    }

    /**
     * Called by subclasses that need their own format, exclusions, or both.
     *
     * @param format the expression a {@code @TmsLink} value must match from its first to its last character
     * @param exclusionsWithReason maps each exclusion key to the justification for it. A key with {@code ..} or
     *     {@code *} is read as an ArchUnit package pattern; any other key names an annotation, and a class or method
     *     bearing it is skipped. The map is defensively copied.
     * @throws NullPointerException if the pattern or the map, or any key or reason in the map, is {@code null}
     */
    protected TmsLinkFormatRule(Pattern format, Map<String, String> exclusionsWithReason) {
        this.expectedContract = EXPECTED_CONTRACT.formatted(
                format.pattern(), DEFAULT_FORMAT.pattern().equals(format.pattern()) ? DEFAULT_EXAMPLES : "");
        this.format = format;
        this.exclusionsWithReason = Map.copyOf(exclusionsWithReason);
    }

    @Override
    public String name() {
        return "TmsLink case id format";
    }

    @Override
    public Severity severity() {
        return Severity.WARNING;
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.TEST);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if an annotation key is carried by no test class or method, or a package pattern
     *     matches no test class
     */
    @Override
    public void verify(ArchUnitContext context) {
        JavaClasses testClasses = context.test();
        TestExclusions.rejectUnmatchedKeys(exclusionsWithReason, testClasses);
        checkViolations(expectedContract, violations(testClasses, format, exclusionsWithReason));
    }

    static List<String> violations(JavaClasses classes, Pattern format, Map<String, String> exclusions) {
        return ArchRuleChecks.violations(definition(format, exclusions), classes);
    }

    static ArchRule definition(Pattern format, Map<String, String> exclusions) {
        return TestExclusions.checkedClasses(exclusions)
                .should(new TmsLinkFormatCondition(
                        format, TestExclusions.annotations(exclusions).keySet()))
                .allowEmptyShould(true)
                .because("an id in the wrong shape cannot be looked up in the test-management system");
    }
}
