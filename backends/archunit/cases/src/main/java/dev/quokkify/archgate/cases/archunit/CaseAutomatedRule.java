package dev.quokkify.archgate.cases.archunit;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.api.ViolationGroup;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks that every existing case linked through {@code @TmsLink} from a test method has an automated automation
 * type.
 *
 * <p>Cases of a manual type are never reported. Ids without an existing case of the checked project are left to
 * {@link CaseExistsRule}. A backend module extends this class with a public no-arg constructor that passes its
 * {@link CaseService} and default type names.
 */
public abstract class CaseAutomatedRule implements ArchitectureRule<ArchUnitContext> {

    private static final String CONTRACT = """
            Every @io.qameta.allure.TmsLink case id on a test method must identify a %s case whose automation type \
            is an automated one%s.""";
    private static final String MANUAL_NOT_REPORTED = "; manual cases are not reported";
    private static final String TITLE = "unexpected automation type";

    private final CaseService service;
    private final String display;
    private final Set<String> automatedTypeNames;
    private final Set<String> manualTypeNames;

    /**
     * Creates the rule for one test-management system.
     *
     * @param service looks up the linked cases
     * @param automatedTypeNames names of the automation types that count as automated; copied, not empty
     * @param manualTypeNames names of the automation types of manual cases, which are never reported; copied
     * @throws NullPointerException if an argument, a name or the service's display name is {@code null}
     * @throws IllegalArgumentException if {@code automatedTypeNames} is empty, a name is blank, a name is in both
     *     sets, or the display name is blank
     */
    protected CaseAutomatedRule(CaseService service, Set<String> automatedTypeNames, Set<String> manualTypeNames) {
        this.service = service;
        this.display = Rules.displayName(service);
        this.automatedTypeNames = Rules.typeNames(automatedTypeNames, "automated");
        this.manualTypeNames = Rules.typeNames(manualTypeNames, "manual");
        if (this.automatedTypeNames.isEmpty()) {
            throw new IllegalArgumentException("At least one automated type name is needed");
        }
        Set<String> both = new TreeSet<>(this.automatedTypeNames);
        both.retainAll(this.manualTypeNames);
        if (!both.isEmpty()) {
            throw new IllegalArgumentException(
                    "Automation types cannot be both automated and manual: " + String.join(", ", both));
        }
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public String name() {
        return display + " case automated";
    }

    @Override
    public Severity severity() {
        return Severity.INFO;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.TEST);
    }

    /**
     * {@inheritDoc}
     *
     * @throws UnableToVerifyError if the cases cannot be loaded, or the service cannot read the automation type of a
     *     linked case, for example because the field holding it is missing
     */
    @Override
    public void verify(ArchUnitContext context) {
        CaseLoad.Loaded loaded = CaseLoad.load(context, service);
        checkViolationGroups(
                CONTRACT.formatted(display, manualTypeNames.isEmpty() ? "" : MANUAL_NOT_REPORTED),
                violations(loaded.linked(), loaded.index(), automatedTypeNames, manualTypeNames));
    }

    /**
     * One group of the linked existing cases whose type is neither automated nor manual; empty without any.
     *
     * @throws UnableToVerifyError if a linked existing case has an {@linkplain AutomationType#unavailable(String)
     *     unavailable} type
     */
    static List<ViolationGroup> violations(
            LinkedCases linked, CaseIndex index, Set<String> automatedTypeNames, Set<String> manualTypeNames) {
        linked.byId().keySet().stream()
                .map(index.existing()::get)
                .flatMap(type -> Optional.ofNullable(type).flatMap(AutomationType::unavailableReason).stream())
                .findFirst()
                .ifPresent(reason -> {
                    throw new UnableToVerifyError("Automation types cannot be checked: " + reason);
                });
        String expected = ", expected '" + String.join(" or ", new TreeSet<>(automatedTypeNames)) + "')";
        List<CaseLines.Item> items = linked.byId().entrySet().stream()
                .flatMap(entry -> Optional.ofNullable(index.existing().get(entry.getKey()))
                        .filter(type -> !accepted(type, automatedTypeNames, manualTypeNames))
                        .map(type ->
                                new CaseLines.Item(entry.getKey(), entry.getValue(), " (actual " + type + expected))
                        .stream())
                .toList();
        return items.isEmpty() ? List.of() : List.of(CaseLines.group(TITLE, items));
    }

    private static boolean accepted(AutomationType type, Set<String> automatedTypeNames, Set<String> manualTypeNames) {
        return type.name()
                .filter(name -> automatedTypeNames.contains(name) || manualTypeNames.contains(name))
                .isPresent();
    }
}
