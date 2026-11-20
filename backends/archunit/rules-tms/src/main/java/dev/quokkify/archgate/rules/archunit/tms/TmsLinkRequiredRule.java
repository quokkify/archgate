package dev.quokkify.archgate.rules.archunit.tms;

import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;

/**
 * Demands an Allure {@code @TmsLink} carrying a non-blank case id on each TestNG test method. When a method repeats
 * the annotation, the ids are read from the {@code @TmsLinks} container. A link on the class or on another method does
 * not help. Allure and TestNG are matched by annotation name only, so the rule needs neither on its own classpath.
 */
public class TmsLinkRequiredRule extends RequiredTestAnnotationRule {

    private static final RequiredAnnotation TMS_LINK = RequiredAnnotation.of("io.qameta.allure.TmsLink")
            .withContainer("io.qameta.allure.TmsLinks")
            .requiringNonBlankValue();

    private static final String EXPECTED_CONTRACT = """
      A TestNG test method needs @io.qameta.allure.TmsLink with a non-blank case id on the method itself; \
      the class or a sibling method having one is not enough.""";

    /** Creates the rule with no exclusions. */
    public TmsLinkRequiredRule() {
        this(Map.of());
    }

    /**
     * Called by subclasses to add exclusions.
     *
     * @param exclusionsWithReason maps each exclusion key to the justification for it. A key with {@code ..} or
     *     {@code *} is read as an ArchUnit package pattern; any other key names an annotation, and a class or method
     *     bearing it is skipped. The map is defensively copied.
     * @throws NullPointerException if the map, or any key or reason in it, is {@code null}
     */
    protected TmsLinkRequiredRule(Map<String, String> exclusionsWithReason) {
        super(TMS_LINK, exclusionsWithReason);
    }

    @Override
    protected String expectedContract() {
        return EXPECTED_CONTRACT;
    }

    static ArchRule definition(Map<String, String> exclusions) {
        return RequiredTestAnnotationRule.definition(TMS_LINK, exclusions);
    }
}
