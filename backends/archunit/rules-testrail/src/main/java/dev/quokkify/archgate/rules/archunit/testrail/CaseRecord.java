package dev.quokkify.archgate.rules.archunit.testrail;

import java.util.Map;
import java.util.Objects;

/**
 * The fields of a TestRail case the rules read.
 *
 * @param id               case id
 * @param suiteId          suite holding the case, {@code null} when the response has none
 * @param deleted          whether TestRail marks the case as deleted
 * @param automationTypeId id of the case's automation type option, {@code null} when not set or when the case has no
 *                         field of the configured name
 */
record CaseRecord(int id, Integer suiteId, boolean deleted, Integer automationTypeId) {

    static final String AUTOMATION_TYPE_FIELD = "custom_automation_type";
    private static final long DELETED = 1L;

    static CaseRecord from(String endpoint, Map<?, ?> json) {
        return from(endpoint, json, AUTOMATION_TYPE_FIELD);
    }

    static CaseRecord from(String endpoint, Map<?, ?> json, String automationField) {
        return new CaseRecord(
                JsonValues.requiredInteger(endpoint, json, "id"),
                JsonValues.integer(json.get("suite_id")).orElse(null),
                isDeleted(json.get("is_deleted")),
                JsonValues.integer(json.get(automationField)).orElse(null));
    }

    private static boolean isDeleted(Object flag) {
        return Objects.equals(flag, DELETED) || Boolean.TRUE.equals(flag);
    }
}
