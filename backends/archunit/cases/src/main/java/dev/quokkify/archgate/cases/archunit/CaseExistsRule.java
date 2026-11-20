package dev.quokkify.archgate.cases.archunit;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Scope;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.ViolationGroup;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Checks that every {@code @TmsLink} case id on a test method names an existing, not deleted case of the checked
 * project in a test-management system.
 *
 * <p>Findings come in up to three groups: ids no case has, deleted cases, and cases of another project. A backend
 * module extends this class with a public no-arg constructor that passes its {@link CaseService}.
 */
public abstract class CaseExistsRule implements ArchitectureRule<ArchUnitContext> {

    private static final String CONTRACT =
            "Every @io.qameta.allure.TmsLink case id on a test method must identify an existing %s case.";

    private final CaseService service;
    private final String display;

    /**
     * Creates the rule for one test-management system.
     *
     * @param service looks up the linked cases
     * @throws NullPointerException if {@code service} or its display name is {@code null}
     * @throws IllegalArgumentException if the display name is blank
     */
    protected CaseExistsRule(CaseService service) {
        this.service = service;
        this.display = Rules.displayName(service);
    }

    @Override
    public Class<ArchUnitContext> contextType() {
        return ArchUnitContext.class;
    }

    @Override
    public String name() {
        return display + " case exists";
    }

    @Override
    public Severity severity() {
        return Severity.INFO;
    }

    @Override
    public Set<Scope> scopes() {
        return EnumSet.of(Scope.TEST);
    }

    @Override
    public void verify(ArchUnitContext context) {
        CaseLoad.Loaded loaded = CaseLoad.load(context, service);
        checkViolationGroups(CONTRACT.formatted(display), violations(display, loaded.linked(), loaded.index()));
    }

    /** The groups in a fixed order, missing, deleted, foreign, leaving out empty ones. */
    static List<ViolationGroup> violations(String display, LinkedCases linked, CaseIndex index) {
        List<CaseLines.Item> missing = new ArrayList<>();
        List<CaseLines.Item> deleted = new ArrayList<>();
        List<CaseLines.Item> foreign = new ArrayList<>();
        linked.byId().forEach((id, methods) -> {
            if (index.missing().contains(id)) {
                missing.add(new CaseLines.Item(id, methods, ""));
            } else if (index.deleted().contains(id)) {
                deleted.add(new CaseLines.Item(id, methods, ""));
            } else if (index.foreign().containsKey(id)) {
                foreign.add(new CaseLines.Item(
                        id, methods, " (project %s)".formatted(index.foreign().get(id))));
            }
        });
        return Stream.of(
                        CaseLines.group("case does not exist in " + display, missing),
                        CaseLines.group("case is deleted in " + display, deleted),
                        CaseLines.group("case belongs to another " + display + " project", foreign))
                .filter(group -> !group.violations().isEmpty())
                .toList();
    }
}
