package dev.quokkify.archgate.cases.archunit;

import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.context.archunit.ArchUnitContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Scans the linked cases and loads their index once per run, shared by every rule that asks with the same inputs. */
final class CaseLoad {

    private static final String INDEX = " index";
    private static final String CHANGED_INDEX = " index (changed files)";

    private CaseLoad() {}

    record Loaded(LinkedCases linked, CaseIndex index) {}

    /** Everything a load depends on; printed with variable names only, since values and settings may be secret. */
    private record Key(Class<?> service, Object shareKey, Map<String, String> variables, CaseQuery query) {

        @Override
        public String toString() {
            return "Key[service=%s, variables=%s, ids=%d, changedFilesOnly=%s]"
                    .formatted(
                            service.getName(),
                            new TreeSet<>(variables.keySet()),
                            query.ids().size(),
                            query.changedFilesOnly());
        }
    }

    private record Shared(Key key, CaseIndex index) {

        @Override
        public String toString() {
            return "Shared[" + key + "]";
        }
    }

    /**
     * Scans the test classes once and returns their links with the index. Without links nothing is asked of the
     * environment or the service, so a project that links no case passes without the service's variables.
     */
    static Loaded load(ArchUnitContext context, CaseService service) {
        Optional<ChangedFiles> changed = ChangedFiles.from(context);
        LinkedCases linked = changed.map(files -> LinkedCases.from(context.test(), files))
                .orElseGet(() -> LinkedCases.from(context.test()));
        if (linked.isEmpty()) {
            return new Loaded(linked, CaseIndex.empty());
        }
        Map<String, String> variables =
                context.environment().require(service.requiredVariables().toArray(String[]::new));
        CaseQuery query = new CaseQuery(linked.ids(), linked.changedFilesOnly());
        Object shareKey = Objects.requireNonNull(service.shareKey(), "service.shareKey()");
        Key key = new Key(service.getClass(), shareKey, Map.copyOf(variables), query);
        return new Loaded(linked, index(context, service, key));
    }

    private static CaseIndex index(ArchUnitContext context, CaseService service, Key key) {
        String display = service.displayName();
        String name = display + (key.query().changedFilesOnly() ? CHANGED_INDEX : INDEX);
        Shared shared = context.shared(name, () -> new Shared(key, build(context, service, key)));
        if (!shared.key().equals(key)) {
            throw new UnableToVerifyError(
                    "%s index was built for other inputs (%s) than this rule needs (%s)%s; rules sharing it must read the same test classes and variables"
                            .formatted(
                                    display,
                                    describe(shared.key()),
                                    describe(key),
                                    differingConfiguration(shared.key(), key) + differingVariables(shared.key(), key)));
        }
        return shared.index();
    }

    private static CaseIndex build(ArchUnitContext context, CaseService service, Key key) {
        String display = service.displayName();
        try {
            CaseIndex index = service.load(key.query(), key.variables());
            if (Objects.isNull(index)) {
                throw new IllegalStateException(service.getClass().getName() + " returned no index");
            }
            List<String> unclassified = key.query().ids().stream()
                    .filter(id -> !index.classifies(id))
                    .map(id -> "C" + id)
                    .toList();
            if (!unclassified.isEmpty()) {
                throw new IllegalStateException(
                        "the service left linked cases unclassified: " + String.join(", ", unclassified));
            }
            return index;
        } catch (CaseServiceUnavailableException unavailable) {
            throw context.environment().unavailable(display, unavailable);
        } catch (RuntimeException failure) {
            throw new UnableToVerifyError(display + " index could not be built: " + failure, failure);
        } catch (AssertionError defect) {
            // The gate reports an AssertionError from verify as a finding; a broken load is no finding.
            throw new UnableToVerifyError(display + " index could not be built: " + defect, defect);
        }
    }

    private static String describe(Key key) {
        return "%s, %d ids".formatted(key.service().getName(), key.query().ids().size());
    }

    private static String differingConfiguration(Key built, Key wanted) {
        return built.shareKey().equals(wanted.shareKey()) ? "" : ", differing service configuration";
    }

    /** Names the variables whose values differ, never the values, which may be secrets. */
    private static String differingVariables(Key built, Key wanted) {
        Set<String> names = new TreeSet<>(built.variables().keySet());
        names.addAll(wanted.variables().keySet());
        String differing = names.stream()
                .filter(name -> !Objects.equals(
                        built.variables().get(name), wanted.variables().get(name)))
                .collect(Collectors.joining(", "));
        return differing.isEmpty() ? "" : ", differing variables " + differing;
    }
}
