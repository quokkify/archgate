package dev.quokkify.archgate.cases.archunit;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where each queried case id stands in the test-management system. Every id sits in at most one category.
 *
 * @param existing ids of cases that exist in the checked project, each with its automation type
 * @param missing ids no case has
 * @param deleted ids of cases that exist but are marked deleted
 * @param foreign ids of cases in another project, each with a label of that project for the finding, such as
 *     its id or name
 */
public record CaseIndex(
        Map<Integer, AutomationType> existing,
        Set<Integer> missing,
        Set<Integer> deleted,
        Map<Integer, String> foreign) {

    private static final CaseIndex EMPTY = new CaseIndex(Map.of(), Set.of(), Set.of(), Map.of());

    /**
     * Validates and copies the categories.
     *
     * @param existing ids of existing cases with their automation types
     * @param missing ids no case has
     * @param deleted ids of deleted cases
     * @param foreign ids of cases in another project with that project's label
     * @throws NullPointerException if a collection, an id, a type or a label is {@code null}
     * @throws IllegalArgumentException if an id is below 1, a label is blank, or an id sits in two categories
     */
    public CaseIndex {
        existing = Map.copyOf(existing);
        missing = Set.copyOf(missing);
        deleted = Set.copyOf(deleted);
        foreign = Map.copyOf(foreign);
        foreign.values().forEach(label -> {
            if (label.isBlank()) {
                throw new IllegalArgumentException("A foreign project label must not be blank");
            }
        });
        Map<Integer, String> seen = new HashMap<>();
        place(seen, existing.keySet(), "existing");
        place(seen, missing, "missing");
        place(seen, deleted, "deleted");
        place(seen, foreign.keySet(), "foreign");
    }

    static CaseIndex empty() {
        return EMPTY;
    }

    boolean classifies(int id) {
        return existing.containsKey(id) || missing.contains(id) || deleted.contains(id) || foreign.containsKey(id);
    }

    private static void place(Map<Integer, String> seen, Set<Integer> ids, String category) {
        for (Integer id : new TreeSet<>(ids)) {
            if (id < 1) {
                throw new IllegalArgumentException("Case ids are positive, got " + id);
            }
            String earlier = seen.putIfAbsent(id, category);
            if (Objects.nonNull(earlier)) {
                throw new IllegalArgumentException(
                        "Case %d is both %s and %s; an id sits in one category only".formatted(id, earlier, category));
            }
        }
    }
}
