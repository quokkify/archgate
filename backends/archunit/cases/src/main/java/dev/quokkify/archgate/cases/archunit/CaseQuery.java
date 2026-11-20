package dev.quokkify.archgate.cases.archunit;

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * What the rules ask a {@link CaseService} to look up.
 *
 * @param ids the linked case ids, positive, sorted and unmodifiable; never empty
 * @param changedFilesOnly whether only the test classes of changed files were scanned, in which case the ids
 *     are usually few and a service may prefer single lookups over listing whole projects
 */
public record CaseQuery(SortedSet<Integer> ids, boolean changedFilesOnly) {

    /**
     * Validates and copies the ids.
     *
     * @param ids the linked case ids
     * @param changedFilesOnly whether only changed test classes were scanned
     * @throws NullPointerException if {@code ids} or an id is {@code null}
     * @throws IllegalArgumentException if {@code ids} is empty or holds an id below 1
     */
    public CaseQuery {
        Objects.requireNonNull(ids, "ids");
        TreeSet<Integer> copy = new TreeSet<>();
        for (Integer id : ids) {
            Objects.requireNonNull(id, "id");
            if (id < 1) {
                throw new IllegalArgumentException("Case ids are positive, got " + id);
            }
            copy.add(id);
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("A case query needs at least one id");
        }
        ids = Collections.unmodifiableSortedSet(copy);
    }
}
