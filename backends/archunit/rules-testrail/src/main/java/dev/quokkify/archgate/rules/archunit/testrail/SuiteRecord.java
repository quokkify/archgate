package dev.quokkify.archgate.rules.archunit.testrail;

import java.util.Map;

/**
 * A TestRail suite and the project it belongs to.
 *
 * @param id        suite id
 * @param projectId project holding the suite
 */
record SuiteRecord(int id, int projectId) {

    static SuiteRecord from(String endpoint, Map<?, ?> json) {
        return new SuiteRecord(
                JsonValues.requiredInteger(endpoint, json, "id"),
                JsonValues.requiredInteger(endpoint, json, "project_id"));
    }
}
