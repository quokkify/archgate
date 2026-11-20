package dev.quokkify.archgate.rules.archunit.testrail;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Where the index loader reads TestRail data from: the HTTP client in production, a stub in tests. */
interface CaseSource {

    /** Number of cases one {@link #suitePage(int, int)} call asks for. */
    int PAGE_SIZE = 250;

    /**
     * Looks up one case.
     *
     * @param id case id
     * @return the case, deleted ones included, or empty when TestRail does not know the id
     */
    Optional<CaseRecord> findCase(int id);

    /**
     * Reads one page of a suite's cases in the project.
     *
     * @param suiteId suite to read
     * @param offset  index of the first case; a page holds up to {@link #PAGE_SIZE} cases
     * @return the page, empty past the last case
     */
    List<CaseRecord> suitePage(int suiteId, int offset);

    /**
     * Reads the case field definitions.
     *
     * @return all case fields
     */
    List<FieldRecord> caseFields();

    /**
     * Reads the ids of every suite in the project.
     *
     * @return suite ids
     */
    Set<Integer> projectSuiteIds();

    /**
     * Finds the project a suite belongs to.
     *
     * @param suiteId suite to look up
     * @return the suite's project id
     */
    int suiteProjectId(int suiteId);

    /**
     * Names the project this source reads.
     *
     * @return project id
     */
    int projectId();
}
